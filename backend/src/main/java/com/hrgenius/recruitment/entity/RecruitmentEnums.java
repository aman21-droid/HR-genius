package com.hrgenius.recruitment.entity;

import java.util.EnumSet;
import java.util.Set;

/** Enumerations for the recruitment module. Persisted as strings. */
public final class RecruitmentEnums {

    private RecruitmentEnums() {
    }

    /** Requisition lifecycle; only OPEN requisitions accept applications. */
    public enum RequisitionStatus { DRAFT, PENDING_APPROVAL, OPEN, ON_HOLD, CLOSED, REJECTED, CANCELLED }

    /** Where a candidate or application came from. */
    public enum CandidateSource { CAREERS_PAGE, REFERRAL, LINKEDIN, AGENCY, DIRECT, OTHER }

    /** Pipeline stage. HIRED, REJECTED and WITHDRAWN are terminal. */
    public enum ApplicationStage {
        APPLIED, SCREENING, INTERVIEW, OFFER, HIRED, REJECTED, WITHDRAWN;

        public static final Set<ApplicationStage> TERMINAL = EnumSet.of(HIRED, REJECTED, WITHDRAWN);

        public boolean isTerminal() {
            return TERMINAL.contains(this);
        }
    }

    public enum ApplicationEventType { STAGE_CHANGED, NOTE, INTERVIEW_SCHEDULED, INTERVIEW_UPDATED, FEEDBACK, OFFER, HIRED }

    public enum InterviewMode { IN_PERSON, VIDEO, PHONE }

    public enum InterviewStatus { SCHEDULED, COMPLETED, CANCELLED, NO_SHOW }

    public enum Recommendation { STRONG_HIRE, HIRE, NO_HIRE, STRONG_NO_HIRE }

    /** Offer lifecycle; the approval engine moves PENDING_APPROVAL to APPROVED or REJECTED. */
    public enum OfferStatus { DRAFT, PENDING_APPROVAL, APPROVED, SENT, ACCEPTED, DECLINED, REJECTED, WITHDRAWN }
}
