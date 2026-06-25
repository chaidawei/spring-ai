package com.david.springai.repository;

import com.david.springai.entity.DssBook;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 图书数据访问层
 */
@Repository
public interface DssBookRepository extends JpaRepository<DssBook, Long> {

    /**
     * 分页查询所有图书（配合 Pageable 限制数量）
     */
    @Query("SELECT b FROM DssBook b ORDER BY b.id ASC")
    List<DssBook> findBooksWithLimit(Pageable pageable);

    /**
     * 增量查询：只查 ID 大于指定值的图书（用于增量同步）
     */
    @Query("SELECT b FROM DssBook b WHERE b.id > :lastId ORDER BY b.id ASC")
    List<DssBook> findBooksAfterId(Long lastId, Pageable pageable);
}
