package no.javazone.cake.redux;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class DataServlet extends HttpServlet {
    private EmsCommunicator emsCommunicator;
    private AcceptorSetter acceptorSetter;

    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String pathInfo = req.getPathInfo();
        if ("/editTalk".equals(pathInfo)) {
            updateTalk(req, resp);
        } else if ("/publishTalk".equals(pathInfo)) {
            publishTalk(req, resp);
        } else if ("/acceptTalks".equals(pathInfo)) {
            acceptTalks(req,resp);
        } else if ("/massUpdate".equals(pathInfo)) {
            massUpdate(req, resp);
        } else if ("/assignRoom".equals(pathInfo)) {
            assignRoom(req,resp);
        } else if ("/assignSlot".equals(pathInfo)) {
            assignSlot(req,resp);
        }

    }

    private void assignRoom(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try (InputStream inputStream = req.getInputStream()) {
            String inputStr = EmsCommunicator.toString(inputStream);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode update = objectMapper.readTree(inputStr);
            String ref = update.get("talkRef").asText();
            String roomRef = update.get("roomRef").asText();

            String lastModified = update.get("lastModified").asText();

            String newTalk = emsCommunicator.assignRoom(ref,roomRef,lastModified);
            resp.getWriter().append(newTalk);
        }

    }

    private void assignSlot(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try (InputStream inputStream = req.getInputStream()) {
            String inputStr = EmsCommunicator.toString(inputStream);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode update = objectMapper.readTree(inputStr);

            String ref = update.get("talkRef").asText();
            String slotRef = update.get("slotRef").asText();

            String lastModified = update.get("lastModified").asText();

            String newTalk = emsCommunicator.assignSlot(ref, slotRef, lastModified);
            resp.getWriter().append(newTalk);
        }

    }


    private void publishTalk(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try (InputStream inputStream = req.getInputStream()) {
            String inputStr = EmsCommunicator.toString(inputStream);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode update = objectMapper.readTree(inputStr);

            String ref = update.get("ref").asText();

            String lastModified = update.get("lastModified").asText();

            String newTalk = emsCommunicator.publishTalk(ref, lastModified);
            resp.getWriter().append(newTalk);
        }

    }

    private void acceptTalks(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try (InputStream inputStream = req.getInputStream()) {
            String inputStr = EmsCommunicator.toString(inputStream);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonObject = objectMapper.readTree(inputStr);

            ArrayNode talks = (ArrayNode) jsonObject.get("talks");
            String statusJson = acceptorSetter.accept(talks);
            resp.getWriter().append(statusJson);
        }
    }

    private void massUpdate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try (InputStream inputStream = req.getInputStream()) {
            String inputStr = EmsCommunicator.toString(inputStream);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonObject = objectMapper.readTree(inputStr);
            String statusJson = acceptorSetter.massUpdate((ObjectNode) jsonObject);
            resp.getWriter().append(statusJson);

        }
    }
    private void updateTalk(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try (InputStream inputStream = req.getInputStream()) {
            String inputStr = EmsCommunicator.toString(inputStream);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode update = objectMapper.readTree(inputStr);

            String ref = update.get("ref").asText();
            ArrayNode tags = (ArrayNode) update.get("tags");
            String state = update.get("state").asText();
            String lastModified = update.get("lastModified").asText();
            List<String> taglist = new ArrayList<>();
            for (int i=0;i<tags.size();i++) {
                taglist.add(tags.get(i).asText());
            }

            String newTalk = emsCommunicator.update(ref, taglist, state, lastModified);
            resp.getWriter().append(newTalk);
        }

    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/json");
        PrintWriter writer = response.getWriter();
        String pathInfo = request.getPathInfo();
        if ("/talks".equals(pathInfo)) {
            String encEvent = request.getParameter("eventId");
            writer.append(emsCommunicator.talkShortVersion(encEvent));
        } else if ("/atalk".equals(pathInfo)) {
            String encTalk = request.getParameter("talkId");
            writer.append(emsCommunicator.fetchOneTalk(encTalk));
        } else if ("/events".equals(pathInfo)) {
            writer.append(emsCommunicator.allEvents());
        } else if ("/roomsSlots".equals(pathInfo)) {
            String encEvent = request.getParameter("eventId");
            writer.append(emsCommunicator.allRoomsAndSlots(encEvent));
        }
    }

    private String config() {
        ObjectNode conf = JsonNodeFactory.instance.objectNode();
        conf.put("submititloc",Configuration.submititLocation());
        return conf.toString();
    }


    @Override
    public void init() throws ServletException {
        emsCommunicator = new EmsCommunicator();
        acceptorSetter = new AcceptorSetter(emsCommunicator);
    }

    public void setEmsCommunicator(EmsCommunicator emsCommunicator) {
        this.emsCommunicator = emsCommunicator;
    }
}
