package no.javazone.cake.redux;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
            ObjectNode update = EmsCommunicator.parse(inputStream);
            String ref = update.get("talkRef").asText();
            String roomRef = update.get("roomRef").asText();

            String lastModified = update.get("lastModified").asText();

            String newTalk = emsCommunicator.assignRoom(ref,roomRef,lastModified);
            resp.getWriter().append(newTalk);
        }
    }

    private void assignSlot(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try (InputStream inputStream = req.getInputStream()) {
            ObjectNode update = EmsCommunicator.parse(inputStream);
            String ref = update.get("talkRef").asText();
            String slotRef = update.get("slotRef").asText();

            String lastModified = update.get("lastModified").asText();

            String newTalk = emsCommunicator.assignSlot(ref, slotRef, lastModified);
            resp.getWriter().append(newTalk);
        }
    }


    private void publishTalk(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try (InputStream inputStream = req.getInputStream()) {
            ObjectNode update = EmsCommunicator.parse(inputStream);
            String ref = update.get("ref").asText();

            String lastModified = update.get("lastModified").asText();

            String newTalk = emsCommunicator.publishTalk(ref,lastModified);
            resp.getWriter().append(newTalk);
        }
    }

    private void acceptTalks(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try (InputStream inputStream = req.getInputStream()) {
            ObjectNode jsonObject = EmsCommunicator.parse(inputStream);
            JsonNode talks = jsonObject.get("talks");
            if (talks.isArray()) {
                String statusJson = acceptorSetter.accept((ArrayNode)talks);
                resp.getWriter().append(statusJson);
            }
        }
    }

    private void massUpdate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try (InputStream inputStream = req.getInputStream()) {
            ObjectNode jsonObject = EmsCommunicator.parse(inputStream);
            String statusJson = acceptorSetter.massUpdate(jsonObject);
            resp.getWriter().append(statusJson);
        }
    }
    private void updateTalk(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try (InputStream inputStream = req.getInputStream()) {
            ObjectNode update = EmsCommunicator.parse(inputStream);
            String ref = update.get("ref").asText();
            JsonNode tags = update.get("tags");
            String state = update.get("state").asText();
            String lastModified = update.get("lastModified").asText();
            List<String> taglist = toCollection(tags);
            String newTalk = emsCommunicator.update(ref, taglist, state,lastModified);
            resp.getWriter().append(newTalk);
        }
    }

    private List<String> toCollection(JsonNode tags) {
        ArrayList<String> result = new ArrayList<>();
        if (tags == null) {
            return result;
        }
        for (JsonNode tag : tags) {
            result.add(tag.asText());
        }
        return result;
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


    @Override
    public void init() throws ServletException {
        emsCommunicator = new EmsCommunicator();
        acceptorSetter = new AcceptorSetter(emsCommunicator);
    }

    public void setEmsCommunicator(EmsCommunicator emsCommunicator) {
        this.emsCommunicator = emsCommunicator;
    }
}
