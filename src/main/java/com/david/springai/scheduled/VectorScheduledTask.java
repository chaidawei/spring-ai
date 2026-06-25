package com.david.springai.scheduled;

import com.david.springai.service.VectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 向量数据定时插入任务
 * 1. 程序启动后自动从MySQL读取图书插入到Milvus（仅当集合为空时）
 * 2. 每天凌晨2点增量同步MySQL新增图书到Milvus（只同步上次之后的新书，节省Embedding API用量）
 */
@Component
public class VectorScheduledTask implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VectorScheduledTask.class);

    private final VectorStoreService vectorStoreService;

    public VectorScheduledTask(VectorStoreService vectorStoreService) {
        this.vectorStoreService = vectorStoreService;
    }

    /**
     * 程序启动后自动执行（ApplicationRunner）
     * Spring Boot 容器完全启动后回调，比 @PostConstruct 更稳定
     * 启动前先检查集合是否已有数据，避免重复插入
     */
    @Override
    public void run(ApplicationArguments args) {
        // 先检查集合是否已有数据，有则跳过避免重复插入
        if (vectorStoreService.hasData()) {
            log.info("========== Milvus已有数据，跳过启动全量加载 ==========");
            return;
        }
        log.info("========== Milvus集合为空，开始从MySQL全量加载图书数据到Milvus ==========");
        long start = System.currentTimeMillis();
        try {
            int inserted = vectorStoreService.insertBooksFromDatabase();
            long elapsed = System.currentTimeMillis() - start;
            log.info("========== 启动全量加载完成：成功插入 {} 条数据，耗时 {} ms ==========", inserted, elapsed);
        } catch (Exception e) {
            // 余额不足等错误不阻止程序启动，仅打印警告
            log.warn("========== 启动加载失败（程序仍可正常运行）：{} ==========", e.getMessage());
        }
    }

    /**
     * 每天凌晨2点执行，增量同步MySQL新增图书到Milvus
     * 只同步上次同步之后新增的图书（按ID递增），避免重复向量化消耗Embedding API用量
     * cron表达式: 秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledInsert() {
        log.info("========== 定时任务开始：增量同步MySQL新增图书到Milvus（lastId={}）==========",
                vectorStoreService.getLastSyncedBookId());
        long start = System.currentTimeMillis();
        try {
            int inserted = vectorStoreService.insertNewBooksFromDatabase();
            long elapsed = System.currentTimeMillis() - start;
            if (inserted > 0) {
                log.info("========== 定时任务完成：增量插入 {} 条新书，耗时 {} ms ==========", inserted, elapsed);
            } else {
                log.info("========== 定时任务完成：无新增图书，跳过 ==========");
            }
        } catch (Exception e) {
            log.error("========== 定时任务失败：{} ==========", e.getMessage(), e);
        }
    }
}
