/*
 * Copyright 2015 Collective, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.collective.celos.ui;

import com.collective.celos.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import j2html.tags.Tag;
import org.eclipse.jetty.util.UrlEncoded;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;

/**
 * Renders the UI HTML.
 */
public class ReactServlet extends HttpServlet {

    private static final String ZOOM_PARAM = "zoom";
    private static final String TIME_PARAM = "time";
    private static final String WF_GROUP_PARAM = "group";
    private static final String GROUPS_TAG = "groups";
    private static final String WORKFLOWS_TAG = "workflows";
    private static final String NAME_TAG = "name";
    private static final String UNLISTED_WORKFLOWS_CAPTION = "Unlisted workflows";
    private static final String DEFAULT_CAPTION = "All Workflows";

    private final ObjectMapper objectMapper = new ObjectMapper();

    protected class MainUI {
        public String currentTime;
        public List<WorkflowGroupRef> rows;
    }

    protected class WorkflowGroupRef {
        public String name;
        public String url;
    }

    protected class WorkflowGroupPOJO {
        public String name;
        public List<String> times;
        public List<WorkflowPOJO> rows;
    }

    protected class WorkflowPOJO {
        public String workflowName;
        public List<SlotPOJO> slots;
    }

    protected class SlotPOJO {
        public String status;
    }


    protected final ObjectMapper mapper = new ObjectMapper();
    protected final ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();

    protected void testWorkflowGroup(String groupName, HttpServletResponse response) throws IOException {
        final WorkflowGroupPOJO xx = new WorkflowGroupPOJO();
        xx.name = groupName;
        xx.times = new ArrayList<>();
        xx.times.add("0000");
        xx.times.add("0001");
        xx.times.add("0002");
        xx.times.add("0003");
        xx.times.add("0004");
        xx.times.add("0005");
        xx.rows = new ArrayList<>();
        xx.rows.add(new WorkflowPOJO());
        xx.rows.get(0).workflowName = "wf 1";
        xx.rows.get(0).slots = new ArrayList<>();
        xx.rows.get(0).slots.add(new SlotPOJO());
        xx.rows.get(0).slots.get(0).status = "ready";
        xx.rows.get(0).slots.add(new SlotPOJO());
        xx.rows.get(0).slots.get(1).status = "wait";
        xx.rows.add(new WorkflowPOJO());
        xx.rows.get(1).workflowName = "wf 1";
        xx.rows.get(1).slots = new ArrayList<>();
        xx.rows.get(1).slots.add(new SlotPOJO());
        xx.rows.get(1).slots.get(0).status = "ready";
        xx.rows.get(1).slots.add(new SlotPOJO());
        xx.rows.get(1).slots.get(1).status = "wait";

        writer.writeValue(response.getOutputStream(), xx);

    }

    protected void testMain(String groupName, HttpServletResponse response) throws IOException {

        final MainUI zz = new MainUI();
        zz.currentTime = "2015-09-15 21:50 UTC";
        zz.rows = new ArrayList<>();
        zz.rows.add(new WorkflowGroupRef());
        zz.rows.get(0).name = "dsada";
        zz.rows.get(0).url = "/react?wf-group=success";
        zz.rows.add(new WorkflowGroupRef());
        zz.rows.get(1).name = "Gr 2";
        zz.rows.get(1).url = "/react?wf-group=success";

        writer.writeValue(response.getOutputStream(), zz);
    }

    protected WorkflowGroupPOJO processWorkflowGroup(UIConfiguration conf, WorkflowGroup g, HttpServletResponse response) throws IOException {

        final WorkflowGroupPOJO group = new WorkflowGroupPOJO();
        group.name = g.getName();
        final NavigableSet<ScheduledTime> tileTimes = conf.getTileTimes().descendingSet();
        group.times = tileTimes.stream()
                .map(tileTime -> HEADER_FORMAT.print(tileTime.getDateTime()))
                .collect(Collectors.toList());

        group.rows = new ArrayList<>();
        for (WorkflowID id : g.getWorkflows()) {
            final WorkflowPOJO workflow = new WorkflowPOJO();
            group.rows.add(workflow);

            WorkflowStatus workflowStatus = conf.getStatuses().get(id);
            if (workflowStatus == null) {
                workflow.workflowName = id.toString();
                continue;
            }
            Map<ScheduledTime, Set<SlotState>> buckets = bucketSlotsByTime(workflowStatus.getSlotStates(), tileTimes);
            workflow.slots = new ArrayList<>();
            for (ScheduledTime tileTime : tileTimes.descendingSet()) {
                Set<SlotState> slots = buckets.get(tileTime);
                final SlotPOJO slot = new SlotPOJO();
                slot.status = slots.iterator().next().getStatus().toString();
                workflow.slots.add(slot);
            }
        }

        return group;
    }


    protected MainUI processMain(UIConfiguration conf) throws IOException {
        final MainUI res = new MainUI();
        res.currentTime = ScheduledTime.now().toString();
        res.rows = new ArrayList<>();
        for (WorkflowGroup g : conf.getGroups()) {
            final WorkflowGroupRef group = new WorkflowGroupRef();
            group.name = g.getName();
            final UrlEncoded url = new UrlEncoded();
            url.put("group", g.getName());
            group.url = "/react?" + url.encode();
            res.rows.add(group);
        }


        return res;
    }


        @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            URL celosURL = (URL) Util.requireNonNull(getServletContext().getAttribute(Main.CELOS_URL_ATTR));
            URL hueURL = (URL) getServletContext().getAttribute(Main.HUE_URL_ATTR);
            File configFile = (File) getServletContext().getAttribute(Main.CONFIG_FILE_ATTR);

            res.setContentType("application/json;charset=utf-8");
            res.setStatus(HttpServletResponse.SC_OK);


            CelosClient client = new CelosClient(celosURL.toURI());
            ScheduledTime end = getDisplayTime(req.getParameter(TIME_PARAM));
            int zoomLevelMinutes = getZoomLevel(req.getParameter(ZOOM_PARAM));
            NavigableSet<ScheduledTime> tileTimes = getTileTimesSet(getFirstTileTime(end, zoomLevelMinutes), zoomLevelMinutes, MAX_MINUTES_TO_FETCH, MAX_TILES_TO_DISPLAY);
            ScheduledTime start = tileTimes.first();
            Set<WorkflowID> workflowIDs = client.getWorkflowList();

            Map<WorkflowID, WorkflowStatus> statuses = fetchStatuses(client, workflowIDs, start, end);

            List<WorkflowGroup> groups;

            if (configFile != null) {
                groups = getWorkflowGroups(new FileInputStream(configFile), workflowIDs);
            } else {
                groups = getDefaultGroups(workflowIDs);
            }

            UIConfiguration conf = new UIConfiguration(start, end, tileTimes, groups, statuses, hueURL);

            final String wfGroup = req.getParameter(WF_GROUP_PARAM);
            if (wfGroup != null) {
                WorkflowGroup workflowGroup = conf.getGroups().get(0);
                for (WorkflowGroup tmp : conf.getGroups()) {
                    if (tmp.getName().equals(wfGroup)) {
                        workflowGroup = tmp;
                        break;
                    }
                }
                final WorkflowGroupPOJO pojo = processWorkflowGroup(conf, workflowGroup, res);
                writer.writeValue(res.getOutputStream(), pojo);
                return;
            }

            final MainUI mainUI = processMain(conf);

            writer.writeValue(res.getOutputStream(), mainUI);

        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    static ScheduledTime getDisplayTime(String timeStr) {
        if (timeStr == null) {
            return ScheduledTime.now();
        } else {
            return new ScheduledTime(timeStr);
        }
    }
    
    static int getZoomLevel(String zoomStr) {
        if (zoomStr == null) {
            return DEFAULT_ZOOM_LEVEL_MINUTES;
        } else {
            int zoom = Integer.parseInt(zoomStr);
            if (zoom < MIN_ZOOM_LEVEL_MINUTES) {
                return MIN_ZOOM_LEVEL_MINUTES;
            } else if (zoom > MAX_ZOOM_LEVEL_MINUTES) {
                return MAX_ZOOM_LEVEL_MINUTES;
            } else {
                return zoom;
            }
        }
    }

    static final Map<SlotState.Status, String> STATUS_TO_SHORT_NAME = new HashMap<SlotState.Status, String>();
    static {
        STATUS_TO_SHORT_NAME.put(SlotState.Status.FAILURE, "fail");
        STATUS_TO_SHORT_NAME.put(SlotState.Status.READY, "rdy&nbsp;");
        STATUS_TO_SHORT_NAME.put(SlotState.Status.RUNNING, "run&nbsp;");
        STATUS_TO_SHORT_NAME.put(SlotState.Status.SUCCESS, "&nbsp;&nbsp;&nbsp;&nbsp;");
        STATUS_TO_SHORT_NAME.put(SlotState.Status.WAIT_TIMEOUT, "time");
        STATUS_TO_SHORT_NAME.put(SlotState.Status.WAITING, "wait");
        if (STATUS_TO_SHORT_NAME.size() != SlotState.Status.values().length) {
            throw new Error("STATUS_TO_SHORT_NAME mapping is incomplete");
        }
    }
    
    private static final int[] ZOOM_LEVEL_MINUTES = new int[]{1, 5, 15, 30, 60, 60*24};
    static final int DEFAULT_ZOOM_LEVEL_MINUTES = 60;
    static final int MIN_ZOOM_LEVEL_MINUTES = 1;
    static final int MAX_ZOOM_LEVEL_MINUTES = 60*24; // Code won't work with higher level, because of toFullDay()
    
    // We never want to fetch more data than for a week from Celos so as not to overload the server
    private static int MAX_MINUTES_TO_FETCH = 7 * 60 * 24;
    private static int MAX_TILES_TO_DISPLAY = 48;
    
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormat.forPattern("dd");
    private static final DateTimeFormatter HEADER_FORMAT = DateTimeFormat.forPattern("HHmm");
    private static final DateTimeFormatter FULL_FORMAT = DateTimeFormat.forPattern("YYYY-MM-dd HH:mm");
    
    static String render(UIConfiguration conf) throws Exception {
        return html().with(makeHead(), makeBody(conf)).render();
    }

    private static Tag makeHead() {
        return head().with(title("Celos"),
                           link().withType("text/css").withRel("stylesheet").withHref("/static/style.css"),
                           script().withType("text/javascript").withSrc("/static/jquery.min.js"),
                           script().withType("text/javascript").withSrc("/static/script.js"));
    }

    private static Tag makeBody(UIConfiguration conf) {
        return body().with(makeTable(conf), div().with(text("(Shift-click a slot to rerun it.)")));
    }

    private static Tag makeTable(UIConfiguration conf) {
        List<Tag> contents = new LinkedList<>();
        contents.addAll(makeTableHeader(conf));
        contents.addAll(makeTableRows(conf));
        return table().withClass("mainTable").with(contents);
    }

    private static List<Tag> makeTableHeader(UIConfiguration conf) {
        return ImmutableList.of(makeDayHeader(conf), makeTimeHeader(conf));
    }

    private static Tag makeDayHeader(UIConfiguration conf) {
        List<Tag> cells = new LinkedList<>();
        cells.add(td().with(unsafeHtml("&nbsp;")));
        for (ScheduledTime time : conf.getTileTimes().descendingSet()) {
            cells.add(makeDay(time));
        }
        return tr().with(cells);
    }

    private static Tag makeDay(ScheduledTime time) {
        if (Util.isFullDay(time.getDateTime())) {
            return td().with(unsafeHtml("&nbsp;" + DAY_FORMAT.print(time.getDateTime()) + "&nbsp;")).withClass("day");
        } else {
            return td().with(unsafeHtml("&nbsp;&nbsp;&nbsp;&nbsp;")).withClass("noDay");
        }
    }
    
    private static Tag makeTimeHeader(UIConfiguration conf) {
        List<Tag> cells = new LinkedList<>();
        cells.add(td(FULL_FORMAT.print(conf.getEnd().getDateTime()) + " UTC").withClass("currentDate"));
        for (ScheduledTime time : conf.getTileTimes().descendingSet()) {
            cells.add(makeHour(time));
        }
        return tr().with(cells);
    }
    
    private static Tag makeHour(ScheduledTime time) {
        return td(HEADER_FORMAT.print(time.getDateTime())).withClass("hour");
    }
    
    private static List<Tag> makeTableRows(UIConfiguration conf) {
        List<Tag> rows = new LinkedList<>();
        for (WorkflowGroup g : conf.getGroups()) {
            rows.addAll(makeGroupRows(conf, g));
        }
        return rows;
    }

    private static List<Tag> makeGroupRows(UIConfiguration conf, WorkflowGroup g) {
        List<Tag> rows = new LinkedList<>();
        rows.add(tr().with(td(g.getName()).withClass("workflowGroup")));
        for (WorkflowID id : g.getWorkflows()) {
            rows.add(makeWorkflowRow(conf, id));
        }
        return rows;
    }

    private static Tag makeWorkflowRow(UIConfiguration conf, WorkflowID id) {
        WorkflowStatus workflowStatus = conf.getStatuses().get(id);
        if (workflowStatus == null) {
            return tr().with(td(id.toString() + " (missing)").withClass("workflow missing"));
        }
        Map<ScheduledTime, Set<SlotState>> buckets = bucketSlotsByTime(workflowStatus.getSlotStates(), conf.getTileTimes());
        List<Tag> cells = new LinkedList<>();
        cells.add(td(id.toString()).withClass("workflow"));
        for (ScheduledTime tileTime : conf.getTileTimes().descendingSet()) {
            Set<SlotState> slots = buckets.get(tileTime);
            String slotClass = "slot " + printTileClass(slots);
            cells.add(td().with(makeTile(conf, slots)).withClass(slotClass));
        }
        return tr().with(cells);
    }

    static String printTileClass(Set<SlotState> slots) {
        if (slots == null) {
            return "";
        } else if (slots.size() == 1) {
            return slots.iterator().next().getStatus().name();
        } else {
            return printMultiSlotClass(slots);
        }
    }

    static String printMultiSlotClass(Set<SlotState> slots) {
        boolean hasIndeterminate = false;
        for (SlotState slot : slots) {
            if (slot.getStatus().getType() == SlotState.StatusType.FAILURE) {
                return SlotState.Status.FAILURE.name();
            } else if (slot.getStatus().getType() == SlotState.StatusType.INDETERMINATE) {
                hasIndeterminate = true;
            }
        }
        if (hasIndeterminate) {
            return SlotState.Status.WAITING.name();
        } else {
            return SlotState.Status.SUCCESS.name();
        }
    }

    private static Tag makeTile(UIConfiguration conf, Set<SlotState> slots) {
        if (slots == null) {
            return unsafeHtml("&nbsp;&nbsp;&nbsp;&nbsp;");
        } else if (slots.size() == 1) {
            return makeSingleSlot(conf, slots.iterator().next());
        } else {
            return makeMultiSlot(conf, slots.size());
        }
    }

    static Tag makeMultiSlot(UIConfiguration conf, int slotsCount) {
        String num = Integer.toString(slotsCount);
        if (num.length() > 4) {
            return unsafeHtml("999+");
        } else {
            return unsafeHtml(num);
        }
    }

    private static Tag makeSingleSlot(UIConfiguration conf, SlotState state) {
        Tag label = unsafeHtml(STATUS_TO_SHORT_NAME.get(state.getStatus()));
        if (conf.getHueURL() != null && state.getExternalID() != null) {
            return a().withHref(printWorkflowURL(conf, state)).withClass("slotLink").attr("data-slot-id", state.getSlotID().toString()).with(label);
        } else {
            return label;
        }
    }

    private static String printWorkflowURL(UIConfiguration conf, SlotState state) {
        return conf.getHueURL().toString() + "/list_oozie_workflow/" + state.getExternalID();
    }

    private Map<WorkflowID, WorkflowStatus> fetchStatuses(CelosClient client, Set<WorkflowID> workflows, ScheduledTime start, ScheduledTime end) throws Exception {
        Map<WorkflowID, WorkflowStatus> statuses = new HashMap<>();
        for (WorkflowID id : workflows) {
            WorkflowStatus status = client.getWorkflowStatus(id, start, end);
            statuses.put(id, status);
        }
        return statuses;
    }

    static Map<ScheduledTime, Set<SlotState>> bucketSlotsByTime(List<SlotState> slotStates, NavigableSet<ScheduledTime> tileTimes) {
        Map<ScheduledTime, Set<SlotState>> buckets = new HashMap<>();
        for (SlotState state : slotStates) {
            ScheduledTime bucketTime = tileTimes.floor(state.getScheduledTime());
            Set<SlotState> slotsForBucket = buckets.get(bucketTime);
            if (slotsForBucket == null) {
                slotsForBucket = new HashSet<>();
                buckets.put(bucketTime, slotsForBucket);
            }
            slotsForBucket.add(state);
        }
        return buckets;
    }
    
    static List<ScheduledTime> getDefaultTileTimes(ScheduledTime now, int zoomLevelMinutes) {
        return getTileTimes(now, zoomLevelMinutes, MAX_MINUTES_TO_FETCH, MAX_TILES_TO_DISPLAY);
    }
    
    static List<ScheduledTime> getTileTimes(ScheduledTime now, int zoomLevelMinutes, int maxMinutesToFetch, int maxTilesToDisplay) {
        int numTiles = getNumTiles(zoomLevelMinutes, maxMinutesToFetch, maxTilesToDisplay);
        List<ScheduledTime> times = new LinkedList<>();
        ScheduledTime t = now;
        for (int i = 1; i <= numTiles; i++) {
            times.add(bucketTime(t, zoomLevelMinutes));
            t = t.minusMinutes(zoomLevelMinutes);
        }
        return times;
    }

    static int getNumTiles(int zoomLevelMinutes, int maxMinutesToFetch, int maxTilesToDisplay) {
        return Math.min(maxMinutesToFetch / zoomLevelMinutes, maxTilesToDisplay);
    }

    static ScheduledTime bucketTime(ScheduledTime t, int zoomLevelMinutes) {
        DateTime dtNow = t.getDateTime();
        DateTime dtFullDay = toFullDay(dtNow);
        DateTime dt = dtFullDay;
        while(dt.isBefore(dtNow)) {
            dt = dt.plusMinutes(zoomLevelMinutes);
        }
        return new ScheduledTime(dt);
    }

    private static DateTime toFullDay(DateTime dt) {
        return dt.withHourOfDay(0).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0);
    }
    
    // Get first tile, e.g. for now=2015-09-01T20:21Z with zoom=5 returns 2015-09-01T20:20Z
    static ScheduledTime getFirstTileTime(ScheduledTime now, int zoomLevelMinutes) {
        DateTime dt = now.getDateTime();
        DateTime nextDay = toFullDay(dt.plusDays(1));
        DateTime t = nextDay;
        while(t.isAfter(dt)) {
            t = t.minusMinutes(zoomLevelMinutes);
        }
        return new ScheduledTime(t);
    }
    
    static NavigableSet<ScheduledTime> getTileTimesSet(ScheduledTime firstTileTime, int zoomLevelMinutes, int maxMinutesToFetch, int maxTilesToDisplay) {
        int numTiles = getNumTiles(zoomLevelMinutes, maxMinutesToFetch, maxTilesToDisplay);
        TreeSet<ScheduledTime> times = new TreeSet<>();
        ScheduledTime t = firstTileTime;
        for (int i = 1; i <= numTiles; i++) {
            times.add(t);
            t = t.minusMinutes(zoomLevelMinutes);
        }
        return times;
    }

    List<WorkflowGroup> getWorkflowGroups(InputStream configFileIS, Set<WorkflowID> expectedWfs) throws IOException {
        JsonNode mainNode = objectMapper.readValue(configFileIS, JsonNode.class);
        List<WorkflowGroup> configWorkflowGroups = new ArrayList();
        Set<WorkflowID> listedWfs = new TreeSet<>();

        for(JsonNode workflowGroupNode: mainNode.get(GROUPS_TAG)) {
            String[] workflowNames = objectMapper.treeToValue(workflowGroupNode.get(WORKFLOWS_TAG), String[].class);

            List<WorkflowID> ids = new ArrayList<>();
            for (String wfName : workflowNames) {
                ids.add(new WorkflowID(wfName));
            }

            String name = workflowGroupNode.get(NAME_TAG).textValue();
            configWorkflowGroups.add(new WorkflowGroup(name, ids));
            listedWfs.addAll(ids);
        }

        TreeSet<WorkflowID> diff = new TreeSet<>(Sets.difference(expectedWfs, listedWfs));
        if (!diff.isEmpty()) {
            configWorkflowGroups.add(new WorkflowGroup(UNLISTED_WORKFLOWS_CAPTION, new ArrayList<>(diff)));
        }
        return configWorkflowGroups;
    }

    private List<WorkflowGroup> getDefaultGroups(Set<WorkflowID> workflows) {
        return ImmutableList.of(new WorkflowGroup(DEFAULT_CAPTION, new LinkedList<WorkflowID>(new TreeSet<WorkflowID>(workflows))));
    }

}