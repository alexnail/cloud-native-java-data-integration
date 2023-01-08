package complaints;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import complaints.query.ComplaintQueryObjectRepository;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.persistence.EntityNotFoundException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest(classes = {ComplaintsRestControllerTest.Config.class, DemoApplication.class}, webEnvironment = MOCK)
public class ComplaintsRestControllerTest {

    @Configuration
    public static class Config {
        @Bean
        public TransactionTemplate tt(PlatformTransactionManager tx) {
            return new TransactionTemplate(tx);
        }
    }

    private final Log log = LogFactory.getLog(getClass());

    private String complaintJson, commentJson;

    @Autowired
    private ComplaintQueryObjectRepository complaints;

    @Autowired
    private TransactionTemplate tt;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Before
    public void setUp() throws Throwable {

        Map<String, Object> map;

        map = new HashMap<>();
        map.put("description", "Why WebLogic, why?");
        map.put("company", "Oracle");
        complaintJson = objectMapper.writeValueAsString(map);

        map = new HashMap<>();
        map.put("comment",
                "we looked into this, and we can't delete WebLogic from the universe.");
        map.put("user", "le");
        map.put("when", new Date());
        commentJson = objectMapper.writeValueAsString(map);

        log.debug("comment JSON: " + commentJson);
        log.debug("complaint JSON: " + complaintJson);
    }

    @Test
    public void createComplaint() throws Throwable {
        newComplaint();
    }

    @Test
    public void createComment() throws Throwable {
        newComment(newComplaint());
    }

    @Test
    public void closeComplaint() throws Throwable {
        closeComplaint(newComplaint());
    }

    @Test
    public void createCommentAfterComplaintIsClosed() throws Throwable {
        String complaint = newComplaint();
        newComment(complaint);
        closeComplaint(complaint);

        int size;

        size = tt.execute(
                status -> complaints.findById(complaint)
                        .orElseThrow(() -> new EntityNotFoundException())
                        .getComments().size());
        Assert.assertEquals(size, 1);

        mockMvc.perform(
                        post("/complaints/" + complaint + "/comments")
                                .contentType(MediaType.APPLICATION_JSON).content(commentJson))
                .andExpect(status().isNotFound());

        size = tt.execute(
                status -> complaints.findById(complaint)
                        .orElseThrow(() -> new EntityNotFoundException())
                        .getComments().size());
        Assert.assertEquals("there should _still_ only be "
                + "one comment, as the complaint " + "is now closed!", size, 1);

    }

    private String newComment(String complaint) throws Throwable {
        MvcResult result = mockMvc
                .perform(post("/complaints/" + complaint + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentJson))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(result)).andExpect(status().isCreated());
        return complaint;
    }

    private void closeComplaint(String complaintId) throws Throwable {
        MvcResult result = mockMvc
                .perform(delete("/complaints/" + complaintId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(complaintJson))
                .andExpect(request().asyncStarted()).andReturn();

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isNotFound());
    }

    private String newComplaint() throws Throwable {
        MvcResult result = mockMvc
                .perform(post("/complaints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(complaintJson))
                .andExpect(request().asyncStarted()).andReturn();

        AtomicReference<String> complaintId = new AtomicReference<>();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(mvcResult -> {
                    String location = mvcResult.getResponse().getHeader("Location");
                    String complaintsPath = "/complaints/";
                    Assert.assertTrue(location.contains(complaintsPath));
                    complaintId.set(location.split(complaintsPath)[1]);
                })
                .andExpect(status().isCreated());

        return complaintId.get();
    }
}