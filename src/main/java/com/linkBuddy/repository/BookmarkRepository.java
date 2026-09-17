package com.linkBuddy.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.linkBuddy.entity.Bookmark;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    List<Bookmark> findByCategoryIgnoreCaseContaining(String category);

    @Query("select distinct b.category from Bookmark b where b.category is not null order by b.category")
    List<String> findDistinctCategories();
}