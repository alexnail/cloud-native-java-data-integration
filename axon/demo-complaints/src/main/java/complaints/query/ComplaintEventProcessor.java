package complaints.query;

import complaints.CommentAddedEvent;
import complaints.ComplaintClosedEvent;
import complaints.ComplaintFiledEvent;
import java.util.Collections;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ComplaintEventProcessor {

    private final ComplaintQueryObjectRepository complaints;

    private final CommentQueryObjectRepository comments;

    // <1>
    @Autowired
    ComplaintEventProcessor(ComplaintQueryObjectRepository complaints,
            CommentQueryObjectRepository comments) {
        this.complaints = complaints;
        this.comments = comments;
    }

    @EventHandler
    public void on(ComplaintFiledEvent cfe) {
        ComplaintQueryObject complaint = new ComplaintQueryObject(cfe.getId(),
                cfe.getComplaint(), cfe.getCompany(), Collections.emptySet(), false);
        complaints.save(complaint);
    }

    @EventHandler
    public void on(CommentAddedEvent cae) {
        ComplaintQueryObject complaint = complaints.findById(cae.getComplaintId()).orElse(null);
        CommentQueryObject comment = new CommentQueryObject(complaint, cae.getCommentId(), cae.getComment(),
                cae.getUser(), cae.getWhen());
        comments.save(comment);
    }

    @EventHandler
    public void on(ComplaintClosedEvent cce) {
        ComplaintQueryObject complaintQueryObject = complaints.findById(cce.getComplaintId()).orElse(null);
        complaintQueryObject.setClosed(true);
        complaints.save(complaintQueryObject);
    }
}
