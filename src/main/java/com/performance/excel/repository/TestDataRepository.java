package com.performance.excel.repository;

import com.performance.excel.entity.TestData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface TestDataRepository extends JpaRepository<TestData, Long> {
    
    @Query("SELECT COUNT(t) FROM TestData t")
    long getTotalCount();
    
    Page<TestData> findAllByOrderById(Pageable pageable);
}
