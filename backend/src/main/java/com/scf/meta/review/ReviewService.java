package com.scf.meta.review;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {

    private final ReviewEventRepository repo;

    public ReviewService(ReviewEventRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String subjectType, String subjectId, String action, String detail, String actor) {
        ReviewEvent e = new ReviewEvent();
        e.subjectType = subjectType;
        e.subjectId = subjectId;
        e.action = action;
        e.detail = detail;
        e.actor = actor == null ? "system" : actor;
        repo.save(e);
    }
}
