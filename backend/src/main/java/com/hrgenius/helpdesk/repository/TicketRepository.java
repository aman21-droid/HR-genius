package com.hrgenius.helpdesk.repository;

import com.hrgenius.helpdesk.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    @Query(value = "SELECT ticket_no_seq.NEXTVAL FROM dual", nativeQuery = true)
    Long nextTicketNumber();

    @Query("""
            select t from Ticket t left join fetch t.assignee
            where t.requester.id = :empId order by t.id desc
            """)
    List<Ticket> findForRequester(@Param("empId") Long employeeId);
}
