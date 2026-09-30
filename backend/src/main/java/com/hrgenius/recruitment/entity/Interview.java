package com.hrgenius.recruitment.entity;

import com.hrgenius.common.entity.BaseEntity;
import com.hrgenius.recruitment.entity.RecruitmentEnums.InterviewMode;
import com.hrgenius.recruitment.entity.RecruitmentEnums.InterviewStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** An interview round; each panelist has one {@link InterviewFeedback} row. */
@Getter
@Setter
@Entity
@Table(name = "interviews")
@SQLRestriction("deleted = 0")
public class Interview extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "interview_seq_gen")
    @SequenceGenerator(name = "interview_seq_gen", sequenceName = "interview_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private JobApplication application;

    @Column(name = "round_name", nullable = false, length = 80)
    private String roundName;

    @Enumerated(EnumType.STRING)
    @Column(name = "interview_mode", nullable = false, length = 20)
    private InterviewMode mode = InterviewMode.VIDEO;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes = 60;

    @Column(name = "location_or_link", length = 300)
    private String locationOrLink;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InterviewStatus status = InterviewStatus.SCHEDULED;

    @OneToMany(mappedBy = "interview", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<InterviewFeedback> feedback = new ArrayList<>();

    public void addPanelist(InterviewFeedback fb) {
        fb.setInterview(this);
        feedback.add(fb);
    }
}
