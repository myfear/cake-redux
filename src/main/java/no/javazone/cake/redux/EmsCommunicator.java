package no.javazone.cake.redux;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.hamnaberg.funclite.Optional;
import net.hamnaberg.funclite.Predicate;
import net.hamnaberg.json.*;
import net.hamnaberg.json.data.JsonObjectFromData;
import net.hamnaberg.json.parser.CollectionParser;
import net.hamnaberg.json.util.PropertyFunctions;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EmsCommunicator {
    private CollectionParser collectionParser = new CollectionParser();

    public String updateTags(String encodedTalkUrl,List<String> tags,String givenLastModified) {
        Property newVals = Property.arrayObject("tags", new ArrayList<Object>(tags));

        return update(encodedTalkUrl, givenLastModified, Arrays.asList(newVals));
    }

    private String update(String encodedTalkUrl, String givenLastModified, List<Property> newVals) {
        String talkUrl = Base64Util.decode(encodedTalkUrl);
        URLConnection connection = openConnection(talkUrl, true);
        String lastModified = connection.getHeaderField("last-modified");

        if (!lastModified.equals(givenLastModified)) {
            ObjectNode errorJson = JsonNodeFactory.instance.objectNode();
            errorJson.put("error","Talk has been updated at " + lastModified + " not " + givenLastModified);
            return errorJson.toString();
        }

        Data data;
        try (InputStream inputStream = connection.getInputStream()) {
            data = collectionParser.parse(inputStream).getFirstItem().get().getData();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        for (Property prop : newVals) {
            data = data.replace(prop);
        }
        Template template = Template.create(data.getDataAsMap().values());

        HttpURLConnection putConnection = openConnection(talkUrl, true);

        putConnection.setDoOutput(true);
        try {
            putConnection.setRequestMethod("PUT");
            putConnection.setRequestProperty("content-type","application/vnd.collection+json");
            putConnection.setRequestProperty("if-unmodified-since",lastModified);
        } catch (ProtocolException e) {
            throw new RuntimeException(e);
        }


        try (OutputStream outputStream = putConnection.getOutputStream()) {
            template.writeTo(outputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return fetchOneTalk(encodedTalkUrl);
    }

    private String confirmTalkMessage(String status, String message) {
        ObjectNode jsonObject = JsonNodeFactory.instance.objectNode();
        jsonObject.put("status",status);
        jsonObject.put("message",message);
        return jsonObject.toString();
    }

    public String confirmTalk(String encodedTalkUrl, String dinner) {
        ObjectNode jsonTalk = fetchOneTalkAsObjectNode(encodedTalkUrl);
        JsonNode tagsarr = jsonTalk.get("tags");
        List<String> tags = new ArrayList<>();
        for (JsonNode n : tagsarr) {
            tags.add(n.asText());
        }
        if (tags.contains("confirmed")) {
            return confirmTalkMessage("error","Talk has already been confirmed");
        }
        if (!tags.contains("accepted")) {
            return confirmTalkMessage("error","Talk is not accepted");
        }
        if ("yes".equals(dinner)) {
            tags.add("dinner");
        }
        tags.add("confirmed");
        String lastModified = jsonTalk.get("lastModified").asText();
        updateTags(encodedTalkUrl,tags, lastModified);

        return confirmTalkMessage("ok","ok");
    }

    public String allEvents()  {
        try {
            URLConnection connection = openConnection(Configuration.emsEventLocation(), false);
            Collection events = collectionParser.parse(openStream(connection));
            List<Item> items = events.getItems();
            ArrayNode eventArray = JsonNodeFactory.instance.arrayNode();
            for (Item item : items) {
                Data data = item.getData();

                String eventname = data.propertyByName("name").flatMap(PropertyFunctions.propertyToValueStringF).get();

                String slug = data.propertyByName("slug").flatMap(PropertyFunctions.propertyToValueStringF).get();
                String href = item.getHref().get().toString();

                href = Base64Util.encode(href);

                ObjectNode event = JsonNodeFactory.instance.objectNode();

                event.put("name",eventname);
                event.put("ref",href);
                event.put("slug",slug);

                eventArray.add(event);
            }
            return eventArray.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String allRoomsAndSlots(String encodedEventid) {
        String eventid = Base64Util.decode(encodedEventid);
        ArrayNode roomArray = allRooms(eventid);
        ArrayNode slotArray = allSlots(eventid);
        ObjectNode all = JsonNodeFactory.instance.objectNode();

        all.set("rooms",roomArray);
        all.set("slots",slotArray);
        return all.toString();
    }

    private ArrayNode allRooms(String eventid) {
        String loc = eventid + "/rooms";
        URLConnection connection = openConnection(loc, false);
        try {
            ArrayNode roomArray = JsonNodeFactory.instance.arrayNode();
            Collection events = new CollectionParser().parse(connection.getInputStream());
            List<Item> items = events.getItems();
            for (Item item : items) {
                Data data = item.getData();
                String roomname = data.propertyByName("name").get().getValue().get().asString();
                String href = item.getHref().get().toString();

                href = Base64Util.encode(href);

                ObjectNode event = JsonNodeFactory.instance.objectNode();

                event.put("name",roomname);
                event.put("ref",href);

                roomArray.add(event);
            }
            return roomArray;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ArrayNode allSlots(String eventid) {
        String loc = eventid + "/slots";
        URLConnection connection = openConnection(loc, false);
        try {
            ArrayNode slotArray = JsonNodeFactory.instance.arrayNode();
            Collection events = collectionParser.parse(openStream(connection));
            List<Item> items = events.getItems();
            for (Item item : items) {
                Data data = item.getData();
                String start = data.propertyByName("start").get().getValue().get().asString();
                String end = data.propertyByName("end").get().getValue().get().asString();
                String href = item.getHref().get().toString();

                SlotTimeFormatter slotTimeFormatter = new SlotTimeFormatter(start + "+" + end);

                href = Base64Util.encode(href);

                ObjectNode slot = JsonNodeFactory.instance.objectNode();

                slot.put("start", slotTimeFormatter.getStart());
                slot.put("end", slotTimeFormatter.getEnd());
                slot.put("length", slotTimeFormatter.getLength());
                slot.put("ref", href);


                slotArray.add(slot);
            }
            return slotArray;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public String fetchOneTalk(String encodedUrl) {
        return fetchOneTalkAsObjectNode(encodedUrl).toString();
    }

    public ObjectNode fetchOneTalkAsObjectNode(String encodedUrl) {
        String url = Base64Util.decode(encodedUrl);
        URLConnection connection = openConnection(url, true);

        try {
            InputStream is = openStream(connection);
            Item talkItem = collectionParser.parse(is).getFirstItem().get();
            ObjectNode jsonObject = readTalk(talkItem, connection);
            String submititLocation = Configuration.submititLocation() + encodedUrl;

            jsonObject.put("submititLoc",submititLocation);
            jsonObject.put("eventId",eventFromTalk(url));
            return jsonObject;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String eventFromTalk(String url) {
        int pos = url.indexOf("/sessions");
        String eventUrl = url.substring(0, pos);
        return Base64Util.encode(eventUrl);
    }

    private InputStream openStream(URLConnection connection) throws IOException {
        InputStream inputStream = connection.getInputStream();
        if (true) { // flip for debug :)
            return inputStream;
        }
        String stream = toString(inputStream);
        System.out.println("***STRAN***");
        System.out.println(stream);
        return new ByteArrayInputStream(stream.getBytes());
    }

    public String assignRoom(String encodedTalk,String encodedRoomRef,String givenLastModified) {
        String talkUrl = Base64Util.decode(encodedTalk);
        String roomRef = Base64Util.decode(encodedRoomRef);
        StringBuilder formData = new StringBuilder();
        try {
            formData.append(URLEncoder.encode("room","UTF-8"));
            formData.append("=");
            formData.append(URLEncoder.encode(roomRef,"UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        HttpURLConnection connection = (HttpURLConnection) openConnection(talkUrl, true);

        String lastModified = connection.getHeaderField("last-modified");

        if (!lastModified.equals(givenLastModified)) {
            ObjectNode errorJson = JsonNodeFactory.instance.objectNode();
            errorJson.put("error","Talk has been updated at " + lastModified + " not " + givenLastModified);

            return errorJson.toString();
        }

        HttpURLConnection postConnection = (HttpURLConnection) openConnection(talkUrl, true);

        postConnection.setDoOutput(true);
        try {
            postConnection.setRequestMethod("POST");
            postConnection.setRequestProperty("content-type","application/x-www-form-urlencoded");
            postConnection.setRequestProperty("if-unmodified-since",lastModified);
        } catch (ProtocolException e) {
            throw new RuntimeException(e);
        }



        try {
            DataOutputStream wr = new DataOutputStream(postConnection.getOutputStream());
            wr.writeBytes(formData.toString());
            wr.flush();
            wr.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (InputStream is = postConnection.getInputStream()) {
            toString(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return fetchOneTalk(encodedTalk);
    }

    public String assignSlot(String encodedTalk,String encodedSlotRef,String givenLastModified) {
        String talkUrl = Base64Util.decode(encodedTalk);
        String slotRef = Base64Util.decode(encodedSlotRef);
        StringBuilder formData = new StringBuilder();
        try {
            formData.append(URLEncoder.encode("slot","UTF-8"));
            formData.append("=");
            formData.append(URLEncoder.encode(slotRef,"UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        HttpURLConnection connection = (HttpURLConnection) openConnection(talkUrl, true);

        String lastModified = connection.getHeaderField("last-modified");

        if (!lastModified.equals(givenLastModified)) {
            ObjectNode errorJson = JsonNodeFactory.instance.objectNode();
            errorJson.put("error","Talk has been updated at " + lastModified + " not " + givenLastModified);
            return errorJson.toString();
        }

        HttpURLConnection postConnection = (HttpURLConnection) openConnection(talkUrl, true);

        postConnection.setDoOutput(true);
        try {
            postConnection.setRequestMethod("POST");
            postConnection.setRequestProperty("content-type","application/x-www-form-urlencoded");
            postConnection.setRequestProperty("if-unmodified-since",lastModified);
        } catch (ProtocolException e) {
            throw new RuntimeException(e);
        }



        try {
            DataOutputStream wr = new DataOutputStream(postConnection.getOutputStream());
            wr.writeBytes(formData.toString());
            wr.flush();
            wr.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (InputStream is = postConnection.getInputStream()) {
            toString(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return fetchOneTalk(encodedTalk);
    }

    public String publishTalk(String encodedTalkUrl,String givenLastModified) {
        String talkUrl = Base64Util.decode(encodedTalkUrl);
        HttpURLConnection connection = openConnection(talkUrl, true);

        String lastModified = connection.getHeaderField("last-modified");
        if (!lastModified.equals(givenLastModified)) {
            ObjectNode errorJson = JsonNodeFactory.instance.objectNode();
            errorJson.put("error","Talk has been updated at " + lastModified + " not " + givenLastModified);
            return errorJson.toString();
        }

        String publishLink;
        try (InputStream inputStream = connection.getInputStream()) {
            Collection parse = collectionParser.parse(inputStream);
            Item talkItem = parse.getFirstItem().get();
            Optional<Link> publish = talkItem.linkByRel("publish");
            publishLink = publish.get().getHref().toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        HttpURLConnection postConnection = openConnection(publishLink, true);

        postConnection.setDoOutput(true);
        try {
            postConnection.setRequestMethod("POST");
            postConnection.setRequestProperty("content-type","text/uri-list");
            postConnection.setRequestProperty("if-unmodified-since",lastModified);
        } catch (ProtocolException e) {
            throw new RuntimeException(e);
        }


        try (OutputStream outputStream = postConnection.getOutputStream()) {
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream))) {
                writer.println(talkUrl);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return fetchOneTalk(encodedTalkUrl);
    }

    public String talkShortVersion(String encodedEvent) {
        List<Item> items = getAllTalksSummary(encodedEvent);
        ArrayNode allTalk = JsonNodeFactory.instance.arrayNode();
        for (Item item : items) {
            ObjectNode jsonTalk = readItemProperties(item, null);
            addSpeakersToTalkFromLink(allTalk, item, jsonTalk);

            readRoom(item,jsonTalk);
            readSlot(item,jsonTalk);
        }
        return allTalk.toString();
    }


    private void addSpeakersToTalkFromLink(ArrayNode allTalk, Item item, ObjectNode jsonTalk) {
        ArrayNode speakers = JsonNodeFactory.instance.arrayNode();
        List<Link> links = item.findLinks(new Predicate<Link>() {
            @Override
            public boolean apply(Link input) {
                return "speaker item".equals(input.getRel());
            }
        });
        for (Link link : links) {
            ObjectNode speaker = JsonNodeFactory.instance.objectNode();
            speaker.put("name", link.getPrompt().get());
            speakers.add(speaker);
        }

        jsonTalk.set("speakers",speakers);
        allTalk.add(jsonTalk);
    }


    public String talksFullVersion(String encodedEvent) {
        List<Item> items = getAllTalksSummary(encodedEvent);
        // TODO There has to be a better way to do this
        ArrayNode talkArray = JsonNodeFactory.instance.arrayNode();
        int num=items.size();
        for (Item item : items) {
            System.out.println(num--);
            URLConnection talkConn = openConnection(item.getHref().get().toString(), true);
            Item talkIktem;

            try (InputStream talkInpStr = talkConn.getInputStream()) {
                talkIktem = collectionParser.parse(talkInpStr).getFirstItem().get();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            ObjectNode jsonTalk = readTalk(talkIktem, talkConn);
            talkArray.add(jsonTalk);
        }

        return talkArray.toString();
    }

    private List<Item> getAllTalksSummary(String encodedEvent) {
        String url = Base64Util.decode(encodedEvent) + "/sessions";

        HttpURLConnection connection = openConnection(url, true);
        Collection events;
        try(InputStream is = openStream(connection)) {
            events = collectionParser.parse(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return events.getItems();
    }

    private ObjectNode readTalk(Item item, URLConnection connection) {
        ObjectNode jsonTalk = readItemProperties(item, connection);

        readRoom(item, jsonTalk);
        readSlot(item, jsonTalk);

        String speakerLink = item.linkByRel("speaker collection").get().getHref().toString();

        URLConnection speakerConnection = openConnection(speakerLink, true);

        Collection speakers;
        try (InputStream speakInpStream = speakerConnection.getInputStream()) {
            speakers = collectionParser.parse(speakInpStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ArrayNode jsonSpeakers = JsonNodeFactory.instance.arrayNode();
        for (Item speaker : speakers.getItems()) {
            ObjectNode jsonSpeaker = readItemProperties(speaker, speakerConnection);
            jsonSpeakers.add(jsonSpeaker);
        }

        jsonTalk.set("speakers", jsonSpeakers);
        return jsonTalk;
    }

    private void readRoom(Item item, ObjectNode jsonTalk) {
        Optional<Link> roomLinkOpt = item.linkByRel("room item");
        if (roomLinkOpt.isSome()) {
            Link roomLink = roomLinkOpt.get();
            String roomName = roomLink.getPrompt().get();
            String ref = roomLink.getHref().toString();
            ObjectNode room = JsonNodeFactory.instance.objectNode();
            room.put("name",roomName);
            room.put("ref", Base64Util.encode(ref));
            jsonTalk.set("room",room);
        }
    }

    private void readSlot(Item item, ObjectNode jsonTalk) {
        Optional<Link> slotLinkOpt = item.linkByRel("slot item");
        if (slotLinkOpt.isSome()) {
            Link slotLink = slotLinkOpt.get();
            String ref = slotLink.getHref().toString();
            String slotcode = slotLink.getPrompt().get();
            SlotTimeFormatter slotTimeFormatter = new SlotTimeFormatter(slotcode);
            ObjectNode slot = JsonNodeFactory.instance.objectNode();
            slot.put("ref", Base64Util.encode(ref));
            slot.put("start",slotTimeFormatter.getStart());
            slot.put("end",slotTimeFormatter.getEnd());
            jsonTalk.set("slot",slot);
        }
    }

    private ObjectNode readItemProperties(Item item, URLConnection connection) {
        ObjectNode data = new JsonObjectFromData().apply(item.getData());

        String href = item.getHref().get().toString();
        data.put("ref", Base64Util.encode(href));

        String lastModified = (connection != null) ? connection.getHeaderField("last-modified") : null;
        if (lastModified != null) {
            data.put("lastModified",lastModified);
        }
        return data;
    }


    private static HttpURLConnection openConnection(String questionUrl, boolean useAuthorization)  {
        try {
            final URL url = new URL(questionUrl);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

            if (useAuthorization) {
                String base64String = Base64Util.encode(String.format("%s:%s", Configuration.getEmsUser(), Configuration.getEmsPassword()));
                urlConnection.setRequestProperty("Authorization", String.format("Basic %s", base64String) );
            }

            return urlConnection;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ObjectNode parse(InputStream inputStream) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode jsonNode = mapper.readTree(inputStream);
            if (jsonNode.isObject()) {
                return (ObjectNode) jsonNode;
            }
            return null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String toString(InputStream inputStream) throws IOException {
        try (Reader reader = new BufferedReader(new InputStreamReader(inputStream, "utf-8"))) {
            StringBuilder result = new StringBuilder();
            int c;
            while ((c = reader.read()) != -1) {
                result.append((char)c);
            }
            return result.toString();
        }
    }

    public String update(String ref, List<String> taglist, String state, String lastModified) {
        Property newTag = Property.arrayObject("tags", new ArrayList<Object>(taglist));
        Property newState = Property.value("state",state);
        return update(ref, lastModified, Arrays.asList(newTag,newState));
    }
}
