package com.scf.meta.review;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewEventRepository extends JpaRepository<ReviewEvent, String> {
    List<ReviewEvent> findBySubjectTypeAndSubjectIdOrderByCreatedAtAsc(String subjectType, String subjectId);
    List<ReviewEvent> findAllByOrderByCreatedAtDesc();
}
