package com.performance.excel.entity;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "test_data")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TestData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @ExcelProperty(value = "ID", index = 0)
    @ColumnWidth(15)
    private Long id;
    
    @Column(name = "name", nullable = false)
    @ExcelProperty(value = "이름", index = 1)
    @ColumnWidth(20)
    private String name;
    
    @Column(name = "description", length = 1000)
    @ExcelProperty(value = "설명", index = 2)
    @ColumnWidth(30)
    private String description;
    
    @Column(name = "value", nullable = false)
    @ExcelProperty(value = "가격", index = 3)
    @ColumnWidth(15)
    private BigDecimal value;
    
    @Column(name = "category", nullable = false)
    @ExcelProperty(value = "카테고리", index = 4)
    @ColumnWidth(20)
    private String category;
    
    @Column(name = "created_at")
    @ExcelProperty(value = "생성일시", index = 5)
    @ColumnWidth(20)
//    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
