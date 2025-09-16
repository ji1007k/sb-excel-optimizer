package com.performance.excel.repository;

import com.performance.excel.entity.TestData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestDataRepository extends JpaRepository<TestData, Long> {
    
    @Query("SELECT COUNT(t) FROM TestData t")
    long getTotalCount();

    List<TestData> findAllByOrderByIdAsc();
    
//    Page<TestData> findAllByOrderById(Pageable pageable);
}
