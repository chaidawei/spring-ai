package com.david.springai.controller;

import com.david.springai.service.VectorStoreService;
import org.springframework.ai.document.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Milvus 向量数据库操作控制器
 * 提供完整的增删改查接口
 */
@RestController
@RequestMapping("/vector")
public class VectorController {

    private final VectorStoreService vectorStoreService;

    public VectorController(VectorStoreService vectorStoreService) {
        this.vectorStoreService = vectorStoreService;
    }

    // ==================== 增（Create） ====================

    /**
     * 批量插入测试文档（自动生成数据）
     * POST /vector/init?count=1000
     */
    @PostMapping("/init")
    public ResponseEntity<Map<String, Object>> initData(
            @RequestParam(value = "count", defaultValue = "1000") int count) {
        Map<String, Object> result = new HashMap<>();
        try {
            long start = System.currentTimeMillis();
            int inserted = vectorStoreService.insertDocuments(count);
            long elapsed = System.currentTimeMillis() - start;
            result.put("code", 200);
            result.put("msg", "数据插入完成");
            result.put("insertedCount", inserted);
            result.put("elapsedMs", elapsed);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "插入失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 插入单条自定义文档
     * POST /vector/add
     * Body: { "content": "文档内容", "metadata": { "category": "科技", "topic": "AI" } }
     */
    @PostMapping("/add")
    public ResponseEntity<Map<String, Object>> addOne(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            String content = (String) body.get("content");
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) body.getOrDefault("metadata", new HashMap<>());
            if (content == null || content.isBlank()) {
                result.put("code", 400);
                result.put("msg", "content不能为空");
                return ResponseEntity.badRequest().body(result);
            }
            String id = vectorStoreService.insertOne(content, metadata);
            result.put("code", 200);
            result.put("msg", "插入成功");
            result.put("id", id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "插入失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 批量插入自定义文档
     * POST /vector/add/batch
     * Body: [ { "content": "文档1", "metadata": {} }, { "content": "文档2", "metadata": {} } ]
     */
    @PostMapping("/add/batch")
    public ResponseEntity<Map<String, Object>> addBatch(@RequestBody List<Map<String, Object>> bodyList) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Document> documents = bodyList.stream().map(body -> {
                String content = (String) body.get("content");
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = (Map<String, Object>) body.getOrDefault("metadata", new HashMap<>());
                return new Document(content, metadata);
            }).toList();
            List<String> ids = vectorStoreService.insertBatch(documents);
            result.put("code", 200);
            result.put("msg", "批量插入成功");
            result.put("insertedCount", ids.size());
            result.put("ids", ids);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "批量插入失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    // ==================== 查（Read） ====================

    /**
     * 相似度搜索
     * GET /vector/search?q=人工智能&topK=5
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam("q") String query,
            @RequestParam(value = "topK", defaultValue = "5") int topK) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> docs = vectorStoreService.search(query, topK);
            result.put("code", 200);
            result.put("query", query);
            result.put("total", docs.size());
            result.put("data", docs);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "搜索失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 按分类搜索
     * GET /vector/search/category?q=人工智能&category=科技&topK=5
     */
    @GetMapping("/search/category")
    public ResponseEntity<Map<String, Object>> searchByCategory(
            @RequestParam("q") String query,
            @RequestParam("category") String category,
            @RequestParam(value = "topK", defaultValue = "5") int topK) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> docs = vectorStoreService.searchByCategory(query, category, topK);
            result.put("code", 200);
            result.put("query", query);
            result.put("category", category);
            result.put("total", docs.size());
            result.put("data", docs);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "搜索失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 按元数据字段搜索
     * GET /vector/search/metadata?q=人工智能&key=topic&value=AI&topK=5
     */
    @GetMapping("/search/metadata")
    public ResponseEntity<Map<String, Object>> searchByMetadata(
            @RequestParam("q") String query,
            @RequestParam("key") String metaKey,
            @RequestParam("value") String metaValue,
            @RequestParam(value = "topK", defaultValue = "5") int topK) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> docs = vectorStoreService.searchByMetadata(query, metaKey, metaValue, topK);
            result.put("code", 200);
            result.put("query", query);
            result.put("filter", metaKey + "=" + metaValue);
            result.put("total", docs.size());
            result.put("data", docs);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "搜索失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 带相似度阈值的搜索
     * GET /vector/search/threshold?q=人工智能&topK=5&threshold=0.5
     */
    @GetMapping("/search/threshold")
    public ResponseEntity<Map<String, Object>> searchWithThreshold(
            @RequestParam("q") String query,
            @RequestParam(value = "topK", defaultValue = "5") int topK,
            @RequestParam(value = "threshold", defaultValue = "0.5") double threshold) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> docs = vectorStoreService.searchWithThreshold(query, topK, threshold);
            result.put("code", 200);
            result.put("query", query);
            result.put("threshold", threshold);
            result.put("total", docs.size());
            result.put("data", docs);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "搜索失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    // ==================== 改（Update） ====================

    /**
     * 更新文档（全量更新：内容+元数据）
     * PUT /vector/update/{id}
     * Body: { "content": "新内容", "metadata": { "category": "科技" } }
     */
    @PutMapping("/update/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable("id") String id,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            String content = (String) body.get("content");
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) body.getOrDefault("metadata", new HashMap<>());
            if (content == null || content.isBlank()) {
                result.put("code", 400);
                result.put("msg", "content不能为空");
                return ResponseEntity.badRequest().body(result);
            }
            String updatedId = vectorStoreService.updateDocument(id, content, metadata);
            result.put("code", 200);
            result.put("msg", "更新成功");
            result.put("id", updatedId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "更新失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 仅更新文档内容
     * PUT /vector/update/{id}/content
     * Body: { "content": "新内容" }
     */
    @PutMapping("/update/{id}/content")
    public ResponseEntity<Map<String, Object>> updateContent(
            @PathVariable("id") String id,
            @RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            String content = body.get("content");
            if (content == null || content.isBlank()) {
                result.put("code", 400);
                result.put("msg", "content不能为空");
                return ResponseEntity.badRequest().body(result);
            }
            String updatedId = vectorStoreService.updateContent(id, content);
            result.put("code", 200);
            result.put("msg", "内容更新成功");
            result.put("id", updatedId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "更新失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 批量更新文档
     * PUT /vector/update/batch
     * Body: { "ids": ["id1","id2"], "contents": ["新内容1","新内容2"], "metadatas": [{},{}] }
     */
    @PutMapping("/update/batch")
    public ResponseEntity<Map<String, Object>> updateBatch(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            @SuppressWarnings("unchecked")
            List<String> ids = (List<String>) body.get("ids");
            @SuppressWarnings("unchecked")
            List<String> contents = (List<String>) body.get("contents");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> metadatas = (List<Map<String, Object>>) body.get("metadatas");
            if (ids == null || contents == null || ids.size() != contents.size()) {
                result.put("code", 400);
                result.put("msg", "ids和contents数量不匹配");
                return ResponseEntity.badRequest().body(result);
            }
            if (metadatas == null) {
                metadatas = ids.stream().map(id -> (Map<String, Object>) new HashMap<String, Object>()).toList();
            }
            int updated = vectorStoreService.updateBatch(ids, contents, metadatas);
            result.put("code", 200);
            result.put("msg", "批量更新成功");
            result.put("updatedCount", updated);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "批量更新失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    // ==================== 删（Delete） ====================

    /**
     * 按ID删除文档
     * DELETE /vector/delete
     * Body: ["id1", "id2", ...]
     */
    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, Object>> delete(@RequestBody List<String> ids) {
        Map<String, Object> result = new HashMap<>();
        try {
            vectorStoreService.delete(ids);
            result.put("code", 200);
            result.put("msg", "删除成功");
            result.put("deletedCount", ids.size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "删除失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 按分类删除文档
     * DELETE /vector/delete/category?category=科技
     */
    @DeleteMapping("/delete/category")
    public ResponseEntity<Map<String, Object>> deleteByCategory(
            @RequestParam("category") String category) {
        Map<String, Object> result = new HashMap<>();
        try {
            int deleted = vectorStoreService.deleteByCategory(category);
            result.put("code", 200);
            result.put("msg", "按分类删除完成");
            result.put("category", category);
            result.put("deletedCount", deleted);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "删除失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
}
