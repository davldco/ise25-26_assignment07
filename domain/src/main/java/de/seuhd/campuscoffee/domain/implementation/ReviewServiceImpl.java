package de.seuhd.campuscoffee.domain.implementation;

import de.seuhd.campuscoffee.domain.configuration.ApprovalConfiguration;
import de.seuhd.campuscoffee.domain.exceptions.ValidationException;
import de.seuhd.campuscoffee.domain.model.objects.Review;
import de.seuhd.campuscoffee.domain.model.objects.User;
import de.seuhd.campuscoffee.domain.ports.api.ReviewService;
import de.seuhd.campuscoffee.domain.ports.data.CrudDataService;
import de.seuhd.campuscoffee.domain.ports.data.PosDataService;
import de.seuhd.campuscoffee.domain.ports.data.ReviewDataService;
import de.seuhd.campuscoffee.domain.ports.data.UserDataService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation of the Review service that handles business logic related to review entities.
 */
@Slf4j
@Service
public class ReviewServiceImpl extends CrudServiceImpl<Review, Long> implements ReviewService {
    private final ReviewDataService reviewDataService;
    private final UserDataService userDataService;
    private final PosDataService posDataService;
    private final ApprovalConfiguration approvalConfiguration;

    public ReviewServiceImpl(@NonNull ReviewDataService reviewDataService,
                             @NonNull UserDataService userDataService,
                             @NonNull PosDataService posDataService,
                             @NonNull ApprovalConfiguration approvalConfiguration) {
        super(Review.class);
        this.reviewDataService = reviewDataService;
        this.userDataService = userDataService;
        this.posDataService = posDataService;
        this.approvalConfiguration = approvalConfiguration;
    }

    @Override
    protected CrudDataService<Review, Long> dataService() {
        return reviewDataService;
    }

    @Override
    @Transactional
    public @NonNull Review upsert(@NonNull Review review) {
        log.info("Upserting review with ID '{}' for POS with ID '{}' by user with ID '{}'...",
                review.getId(), review.pos().getId(), review.author().getId());

        // validate that the POS exists
        posDataService.getById(review.pos().getId());

        // validate that a user cannot create more than one review per POS
        List<Review> existingReviews = reviewDataService.filter(review.pos(), review.author());
        if (!existingReviews.isEmpty()) {
            throw new ValidationException("User cannot create more than one review per POS.");
        }

        // set initial approval count and status for new reviews
        Review reviewToSave = review;
        if (review.getId() == null) {
            reviewToSave = review.toBuilder()
                    .approvalCount(0)
                    .approved(false)
                    .build();
        } else {
            // for updates, recalculate approval status based on current approval count
            reviewToSave = updateApprovalStatus(review);
        }

        return super.upsert(reviewToSave);
    }

    @Override
    @Transactional(readOnly = true)
    public @NonNull List<Review> filter(@NonNull Long posId, @NonNull Boolean approved) {
        return reviewDataService.filter(posDataService.getById(posId), approved);
    }

    @Override
    @Transactional
    public @NonNull Review approve(@NonNull Review review, @NonNull Long userId) {
        log.info("Processing approval request for review with ID '{}' by user with ID '{}'...",
                review.getId(), userId);

        // validate that the user exists
        User user = userDataService.getById(userId);

        // validate that the review exists
        Review existingReview = reviewDataService.getById(review.getId());

        // a user cannot approve their own review
        if (existingReview.author().getId().equals(user.getId())) {
            throw new ValidationException("Users cannot approve their own reviews.");
        }

        // increment approval count
        Review updatedReview = existingReview.toBuilder()
                .approvalCount(existingReview.approvalCount() + 1)
                .build();

        // update approval status to determine if the review now reaches the approval quorum
        updatedReview = updateApprovalStatus(updatedReview);

        return reviewDataService.upsert(updatedReview);
    }

    /**
     * Calculates and updates the approval status of a review based on the approval count.
     * Business rule: A review is approved when it reaches the configured minimum approval count threshold.
     *
     * @param review The review to calculate approval status for
     * @return The review with updated approval status
     */
    Review updateApprovalStatus(Review review) {
        log.debug("Updating approval status of review with ID '{}'...", review.getId());
        return review.toBuilder()
                .approved(isApproved(review))
                .build();
    }
    
    /**
     * Determines if a review meets the minimum approval threshold.
     * 
     * @param review The review to check
     * @return true if the review meets or exceeds the minimum approval count, false otherwise
     */
    private boolean isApproved(Review review) {
        return review.approvalCount() >= approvalConfiguration.minCount();
    }
}
