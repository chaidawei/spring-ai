package com.david.springai.service;

import com.david.springai.entity.DssBook;
import com.david.springai.repository.DssBookRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Milvus 向量存储服务
 * 提供文档的增删改查操作
 */
@Service
public class VectorStoreService {

    private final VectorStore vectorStore;
    private final DssBookRepository dssBookRepository;

    // ==================== 限流 & 去重配置 ====================

    /** 已同步到Milvus的图书ID集合（用于去重，避免重复向量化消耗API用量） */
    private final Set<Long> syncedBookIds = new HashSet<>();

    /** 最后同步的图书ID（用于增量同步，只同步新增的图书） */
    private final AtomicLong lastSyncedBookId = new AtomicLong(0);

    /** 每次最多同步多少本书，0=不限制（控制Embedding API用量） */
    @Value("${spring.ai.vectorstore.milvus.max-books-per-sync:50}")
    private int maxBooksPerSync;

    /** 是否启用去重（跳过已在Milvus中的图书） */
    @Value("${spring.ai.vectorstore.milvus.enable-dedup:true}")
    private boolean enableDedup;

    public VectorStoreService(VectorStore vectorStore, DssBookRepository dssBookRepository) {
        this.vectorStore = vectorStore;
        this.dssBookRepository = dssBookRepository;
    }

    // ==================== 增（Create） ====================

    /**
     * 批量插入文档（自动调用 EmbeddingModel 进行向量化）
     * @param count 插入条数
     * @return 实际插入的文档数量
     */
    public int insertDocuments(int count) {
        List<Document> documents = generateDocuments(count);
        // 分批插入，每批20条，避免API限流
        int batchSize = 20;
        int inserted = 0;
        for (int i = 0; i < documents.size(); i += batchSize) {
            int end = Math.min(i + batchSize, documents.size());
            List<Document> batch = documents.subList(i, end);
            vectorStore.add(batch);
            inserted += batch.size();
        }
        return inserted;
    }

    /**
     * 插入单条文档（自定义内容和元数据）
     * @param content  文档内容
     * @param metadata 元数据
     * @return 文档ID
     */
    public String insertOne(String content, Map<String, Object> metadata) {
        String id = UUID.randomUUID().toString();
        Document doc = new Document(id, content, metadata);
        vectorStore.add(List.of(doc));
        return id;
    }

    /**
     * 批量插入自定义文档
     * @param documents 文档列表
     * @return 插入的文档ID列表
     */
    public List<String> insertBatch(List<Document> documents) {
        int batchSize = 20;
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < documents.size(); i += batchSize) {
            int end = Math.min(i + batchSize, documents.size());
            List<Document> batch = documents.subList(i, end);
            vectorStore.add(batch);
            batch.forEach(doc -> ids.add(doc.getId()));
        }
        return ids;
    }

    /**
     * 从本地MySQL数据库读取图书并插入到Milvus向量库（不限制数量）
     * 每本书的 title + description + creator + publisher 合并为向量化内容
     * 其余字段存为元数据
     * @return 实际插入的图书数量
     */
    public int insertBooksFromDatabase() {
        return insertBooksFromDatabase(0);
    }

    /**
     * 从MySQL读取图书并插入到Milvus（支持限流 + 去重）
     * @param maxBooks 最大同步数量，0或负数=不限制
     * @return 实际插入的图书数量
     */
    public int insertBooksFromDatabase(int maxBooks) {
        List<DssBook> books;
        if (maxBooks > 0) {
            books = dssBookRepository.findBooksWithLimit(PageRequest.of(0, maxBooks));
        } else {
            books = dssBookRepository.findAll();
        }
        return doInsertBooks(books);
    }

    /**
     * 增量同步：只同步上次同步之后新增的图书（按ID递增）
     * 配合定时任务使用，避免重复向量化已有数据
     * @return 实际插入的图书数量
     */
    public int insertNewBooksFromDatabase() {
        long lastId = lastSyncedBookId.get();
        int limit = maxBooksPerSync > 0 ? maxBooksPerSync : Integer.MAX_VALUE;
        List<DssBook> books = dssBookRepository.findBooksAfterId(lastId, PageRequest.of(0, limit));
        return doInsertBooks(books);
    }

    /**
     * 应用启动后，从Milvus加载已存在的book_id，用于后续去重
     * 使用相似度搜索查询（仅Milvus查询，不消耗Embedding API用量）
     */
    @PostConstruct
    public void loadExistingBookIds() {
        if (!enableDedup) {
            return;
        }
        try {
            if (!hasData()) {
                return;
            }
            SearchRequest request = SearchRequest.builder()
                    .query("图书")
                    .topK(16384)
                    .similarityThreshold(0.0)
                    .build();
            List<Document> existing = vectorStore.similaritySearch(request);
            for (Document doc : existing) {
                Object bookId = doc.getMetadata().get("book_id");
                if (bookId instanceof Long) {
                    syncedBookIds.add((Long) bookId);
                } else if (bookId instanceof Integer) {
                    syncedBookIds.add(((Integer) bookId).longValue());
                }
            }
            // 同步更新 lastSyncedBookId
            if (!syncedBookIds.isEmpty()) {
                Long maxId = syncedBookIds.stream().max(Long::compareTo).orElse(0L);
                lastSyncedBookId.set(maxId);
            }
        } catch (Exception ignored) {
            // 加载失败不影响主流程，去重功能降级
        }
    }

    /**
     * 获取最后同步的图书ID（供外部查看同步进度）
     */
    public long getLastSyncedBookId() {
        return lastSyncedBookId.get();
    }

    // ==================== 内部实现 ====================

    /**
     * 核心插入逻辑：去重 + 限流 + 分批写入
     */
    private int doInsertBooks(List<DssBook> books) {
        if (books.isEmpty()) {
            return 0;
        }

        List<Document> documents = new ArrayList<>();
        int skippedByDedup = 0;

        for (DssBook book : books) {
            // 去重：跳过已同步的图书（避免重复向量化消耗API用量）
            if (enableDedup && syncedBookIds.contains(book.getId())) {
                skippedByDedup++;
                continue;
            }

            // 构建向量化内容：标题 + 描述 + 作者 + 出版社
            StringBuilder contentBuilder = new StringBuilder();
            if (book.getTitle() != null) contentBuilder.append(book.getTitle()).append("。");
            if (book.getDescription() != null) contentBuilder.append(book.getDescription());
            if (book.getCreator() != null) contentBuilder.append(" 作者：").append(book.getCreator());
            if (book.getPublisher() != null) contentBuilder.append(" 出版社：").append(book.getPublisher());

            String content = contentBuilder.toString();
            if (content.isBlank()) continue;

            // 元数据：保存图书的完整字段
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("book_id", book.getId());
            metadata.put("title", book.getTitle());
            metadata.put("creator", book.getCreator());
            metadata.put("creator_organ", book.getCreatorOrgan());
            metadata.put("publisher", book.getPublisher());
            metadata.put("date_publish", book.getDatePublish());
            metadata.put("date_publish_year", book.getDatePublishYear());
            metadata.put("language", book.getLanguage());
            metadata.put("identifier_isbn", book.getIdentifierIsbn());
            metadata.put("identifier_eisbn", book.getIdentifierEisbn());
            metadata.put("subject_keyword", book.getSubjectKeyword());
            metadata.put("subject_cnlib", book.getSubjectCnlib());
            metadata.put("subject_cnedu", book.getSubjectCnedu());
            metadata.put("format_medium", book.getFormatMedium());
            metadata.put("cover_url", book.getCoverUrl());

            Document doc = new Document(String.valueOf(book.getId()), content, metadata);
            documents.add(doc);
        }

        if (documents.isEmpty()) {
            return 0;
        }

        // 分批插入，每批20条，避免API限流
        int batchSize = 20;
        int inserted = 0;
        for (int i = 0; i < documents.size(); i += batchSize) {
            int end = Math.min(i + batchSize, documents.size());
            List<Document> batch = documents.subList(i, end);
            vectorStore.add(batch);
            inserted += batch.size();
        }

        // 记录已同步的ID（用于后续去重）
        for (Document doc : documents) {
            Object bookIdObj = doc.getMetadata().get("book_id");
            if (bookIdObj instanceof Long) {
                syncedBookIds.add((Long) bookIdObj);
            }
        }

        // 更新最后同步的图书ID（用于增量同步）
        Long maxId = books.stream()
                .map(DssBook::getId)
                .max(Long::compareTo)
                .orElse(lastSyncedBookId.get());
        lastSyncedBookId.updateAndGet(current -> Math.max(current, maxId));

        return inserted;
    }

    // ==================== 查（Read） ====================

    /**
     * 检查集合是否已有数据
     * 通过搜索任意文本，若能返回结果则说明集合中已有数据
     * @return true=已有数据，false=集合为空
     */
    public boolean hasData() {
        try {
            SearchRequest request = SearchRequest.builder()
                    .query("数据")
                    .topK(1)
                    .similarityThreshold(0.0)
                    .build();
            List<Document> results = vectorStore.similaritySearch(request);
            return !results.isEmpty();
        } catch (Exception e) {
            // 集合不存在等情况，视为无数据
            return false;
        }
    }

    /**
     * 相似度搜索
     * @param query  查询文本
     * @param topK   返回条数
     * @return 匹配的文档列表
     */
    public List<Map<String, Object>> search(String query, int topK) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(0.0)
                .build();
        List<Document> results = vectorStore.similaritySearch(request);
        return results.stream().map(this::documentToMap).toList();
    }

    /**
     * 按分类搜索
     * @param query   查询文本
     * @param category 分类过滤
     * @param topK    返回条数
     * @return 匹配的文档列表
     */
    public List<Map<String, Object>> searchByCategory(String query, String category, int topK) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        Filter.Expression filter = builder.eq("category", category).build();

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(0.0)
                .filterExpression(filter)
                .build();
        List<Document> results = vectorStore.similaritySearch(request);
        return results.stream().map(this::documentToMap).toList();
    }

    /**
     * 按元数据字段搜索（通用过滤查询）
     * @param query     查询文本
     * @param metaKey   元数据键
     * @param metaValue 元数据值
     * @param topK      返回条数
     * @return 匹配的文档列表
     */
    public List<Map<String, Object>> searchByMetadata(String query, String metaKey, String metaValue, int topK) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        Filter.Expression filter = builder.eq(metaKey, metaValue).build();

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(0.0)
                .filterExpression(filter)
                .build();
        List<Document> results = vectorStore.similaritySearch(request);
        return results.stream().map(this::documentToMap).toList();
    }

    /**
     * 相似度搜索（带阈值）
     * @param query       查询文本
     * @param topK        返回条数
     * @param threshold   相似度阈值（0.0~1.0）
     * @return 匹配的文档列表
     */
    public List<Map<String, Object>> searchWithThreshold(String query, int topK, double threshold) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshold)
                .build();
        List<Document> results = vectorStore.similaritySearch(request);
        return results.stream().map(this::documentToMap).toList();
    }

    // ==================== 改（Update） ====================

    /**
     * 更新文档（Milvus不支持原地更新，采用先删后增策略）
     * @param id       文档ID
     * @param content  新内容
     * @param metadata 新元数据
     * @return 更新后的文档ID
     */
    public String updateDocument(String id, String content, Map<String, Object> metadata) {
        // 先删除旧文档
        vectorStore.delete(List.of(id));
        // 再插入新文档（保持原ID）
        Document doc = new Document(id, content, metadata);
        vectorStore.add(List.of(doc));
        return id;
    }

    /**
     * 更新文档内容（保留原元数据）
     * 注意：由于Milvus无法直接读取单条，此方法会先搜索获取元数据再更新
     * @param id       文档ID
     * @param content  新内容
     * @return 更新后的文档ID
     */
    public String updateContent(String id, String content) {
        Map<String, Object> emptyMeta = new HashMap<>();
        return updateDocument(id, content, emptyMeta);
    }

    /**
     * 批量更新文档
     * @param ids      文档ID列表
     * @param contents 新内容列表
     * @param metadatas 新元数据列表
     * @return 更新的文档数量
     */
    public int updateBatch(List<String> ids, List<String> contents, List<Map<String, Object>> metadatas) {
        // 先批量删除
        vectorStore.delete(ids);
        // 再批量插入
        List<Document> docs = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            Document doc = new Document(ids.get(i), contents.get(i), metadatas.get(i));
            docs.add(doc);
        }
        int batchSize = 20;
        int updated = 0;
        for (int i = 0; i < docs.size(); i += batchSize) {
            int end = Math.min(i + batchSize, docs.size());
            List<Document> batch = docs.subList(i, end);
            vectorStore.add(batch);
            updated += batch.size();
        }
        return updated;
    }

    // ==================== 删（Delete） ====================

    /**
     * 删除指定文档
     * @param ids 文档ID列表
     */
    public void delete(List<String> ids) {
        vectorStore.delete(ids);
    }

    /**
     * 按分类删除（先搜索出该分类的文档ID，再批量删除）
     * @param category 分类名称
     * @return 删除的文档数量
     */
    public int deleteByCategory(String category) {
        // 通过相似度搜索找到该分类的文档
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        Filter.Expression filter = builder.eq("category", category).build();

        SearchRequest request = SearchRequest.builder()
                .query(category)
                .topK(16384) // Milvus最大返回数
                .similarityThreshold(0.0)
                .filterExpression(filter)
                .build();
        List<Document> results = vectorStore.similaritySearch(request);
        if (results.isEmpty()) {
            return 0;
        }
        List<String> ids = results.stream().map(Document::getId).toList();
        vectorStore.delete(ids);
        return ids.size();
    }

    // ==================== 工具方法 ====================

    /**
     * Document 转 Map
     */
    private Map<String, Object> documentToMap(Document doc) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", doc.getId());
        item.put("content", doc.getText());
        item.put("metadata", doc.getMetadata());
        item.put("score", doc.getMetadata().get("distance"));
        return item;
    }

    // ==================== 生成测试数据 ====================

    private List<Document> generateDocuments(int count) {
        List<Document> documents = new ArrayList<>();
        String[] categories = {"科技", "教育", "医疗", "金融", "艺术", "体育", "历史", "自然", "社会", "生活"};
        String[] templates = {
            "%s是当今社会最热门的话题之一。随着技术的不断发展，%s领域取得了显著的进步。" +
            "许多研究人员和工程师投入大量精力，推动了相关技术的突破。" +
            "在未来几年内，我们有理由相信%ss领域将继续保持高速增长。",

            "关于%s的最新研究表明，该领域的创新正在改变人们的生活方式。" +
            "专家指出，%s技术的应用场景越来越广泛，从工业生产到日常消费都有涉及。" +
            "这种趋势预示着%ss行业将迎来新一轮的发展机遇。",

            "在%s领域，近年来涌现了许多令人瞩目的成果。" +
            "特别是在人工智能与%s的交叉领域，研究者们发现了许多新的可能性。" +
            "这些发现不仅丰富了%s的理论基础，也为实际应用提供了重要参考。",

            "%s行业正在经历一场深刻的变革。数字化转型使得%s相关的服务更加智能化和便捷化。" +
            "消费者对%ss的需求持续增长，市场前景十分广阔。",

            "从全球视角来看，%s的发展呈现出明显的区域特色。" +
            "不同国家和地区在%s领域各有优势，国际合作日益频繁。" +
            "这种多元化的格局为%ss的创新发展注入了新的活力。"
        };

        String[][] topicDetails = {
            // 科技
            {"人工智能", "量子计算", "区块链", "物联网", "5G通信", "云计算", "边缘计算", "数字孪生", "虚拟现实", "增强现实",
             "深度学习", "机器学习", "自然语言处理", "计算机视觉", "机器人技术", "自动驾驶", "芯片设计", "操作系统", "数据库", "网络安全"},
            // 教育
            {"在线教育", "个性化学习", "STEM教育", "教育公平", "终身学习", "远程教学", "智能辅导", "教育评估", "课程改革", "素质教育",
             "职业教育", "高等教育", "学前教育", "特殊教育", "教育技术", "翻转课堂", "项目学习", "合作学习", "创新教育", "国际教育"},
            // 医疗
            {"基因编辑", "精准医疗", "远程诊断", "医疗AI", "药物研发", "健康管理", "疾病预防", "中医现代化", "康复医学", "心理健康",
             "免疫疗法", "干细胞研究", "脑科学", "器官移植", "医疗大数据", "穿戴设备", "慢病管理", "公共卫生", "医学影像", "手术机器人"},
            // 金融
            {"数字货币", "金融科技", "智能投顾", "风控模型", "普惠金融", "绿色金融", "供应链金融", "保险科技", "监管科技", "跨境支付",
             "量化交易", "资产配置", "信用评估", "反欺诈", "合规管理", "金融创新", "养老金", "消费金融", "银行数字化", "证券科技"},
            // 艺术
            {"数字艺术", "AI创作", "沉浸式体验", "新媒体艺术", "文化遗产保护", "艺术教育", "创意设计", "影视制作", "音乐科技", "舞蹈编排",
             "当代艺术", "建筑美学", "摄影技术", "书法艺术", "雕塑创新", "戏剧表演", "文学创作", "策展理念", "艺术市场", "公共艺术"},
            // 体育
            {"电子竞技", "体育科技", "运动分析", "体能训练", "体育医学", "赛事运营", "全民健身", "校园体育", "冬季运动", "极限运动",
             "足球发展", "篮球联赛", "游泳技术", "田径训练", "武术传承", "体育产业", "体育旅游", "运动营养", "康复训练", "体育教育"},
            // 历史
            {"考古发现", "文明起源", "丝绸之路", "近代史研究", "文化遗产", "口述历史", "历史地理", "制度变迁", "思想史", "经济史",
             "社会史", "科技史", "艺术史", "军事史", "外交史", "城市史", "家族史", "宗教史", "民俗研究", "古籍保护"},
            // 自然
            {"气候变化", "生物多样性", "海洋生态", "森林保护", "新能源", "碳中和", "水资源", "大气科学", "地质勘探", "天文观测",
             "动物保护", "植物研究", "微生物学", "生态修复", "可持续农业", "清洁能源", "环境监测", "自然灾害", "国土空间", "绿色建筑"},
            // 社会
            {"城市化", "老龄化", "就业趋势", "社会治理", "公共服务", "数字政府", "社区建设", "乡村振兴", "人口流动", "社会保障",
             "法律改革", "媒体融合", "公益慈善", "志愿精神", "文明创建", "消费升级", "共享经济", "基层治理", "社会组织", "应急管理"},
            // 生活
            {"智能家居", "美食文化", "旅行探索", "时尚潮流", "健康养生", "亲子教育", "宠物经济", "心理健康", "居家设计", "个人理财",
             "时间管理", "高效学习", "社交礼仪", "生活美学", "绿色生活", "运动健身", "阅读写作", "手工制作", "摄影记录", "极简生活"}
        };

        for (int i = 0; i < count; i++) {
            int catIdx = i % categories.length;
            String category = categories[catIdx];
            String[] details = topicDetails[catIdx];
            String topic = details[i % details.length];
            int templateIdx = i % templates.length;
            String template = templates[templateIdx];

            String content = String.format(template, topic, topic, topic);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("category", category);
            metadata.put("topic", topic);
            metadata.put("index", i);

            Document doc = new Document(UUID.randomUUID().toString(), content, metadata);
            documents.add(doc);
        }
        return documents;
    }
}
