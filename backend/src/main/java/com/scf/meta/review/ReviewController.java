package com.scf.meta.review;

import com.scf.meta.common.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/review-events")
public class ReviewController {

    private final ReviewEventRepository reviewRepo;

    public ReviewController(ReviewEventRepository reviewRepo) {
        this.reviewRepo = reviewRepo;
    }

    @GetMapping
    public ApiResponse<List<ReviewEvent>> list(@RequestParam(required = false) String subjectType,
                                               @RequestParam(required = false) String subjectId) {
        if (subjectType != null && subjectId != null) {
            return ApiResponse.ok(reviewRepo
                    .findBySubjectTypeAndSubjectIdOrderByCreatedAtAsc(subjectType, subjectId));
        }
        return ApiResponse.ok(reviewRepo.findAllByOrderByCreatedAtDesc());
    }
}
