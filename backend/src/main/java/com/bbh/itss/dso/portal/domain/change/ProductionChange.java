package com.bbh.itss.dso.portal.domain.change;

import com.bbh.itss.dso.portal.domain.catalog.DeployTarget;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.catalog.Service;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Planning;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static com.bbh.itss.dso.portal.domain.shared.Text.abbreviate;
import static com.bbh.itss.dso.portal.domain.shared.Text.bytes;
import static com.bbh.itss.dso.portal.domain.shared.Text.isBlank;
import static java.util.stream.Collectors.joining;

public record ProductionChange(Long id, String number, Long productId, String productCode, String productName,
                               String departmentName, String fixVersion, ChangeSchedule schedule,
                               String shortDescription, String description, ChangeTemplate template,
                               List<String> epicKeys, List<String> storyKeys, List<ChangeTask> tasks, String url,
                               Instant createdAt) {

    public static final int FIX_VERSION_MAX = 100;
    public static final int SHORT_DESCRIPTION_MAX = 160;
    public static final int DESCRIPTION_MAX = 4000;
    static final int SECTION_MAX = 300;

    public ProductionChange {
        epicKeys = List.copyOf(epicKeys);
        storyKeys = List.copyOf(storyKeys);
        tasks = List.copyOf(tasks);
    }

    public static ProductionChange draft(Product product, String departmentName, List<Service> services,
                                         String fixVersion, ChangeSchedule schedule, ChangeTemplate template,
                                         List<JiraIssue> epics, List<JiraIssue> stories, String shortDescription,
                                         String description) {
        ChangeTemplate raised = template.releasedAs(fixVersion);
        String summary = isBlank(shortDescription) ? shortDescriptionOf(product, fixVersion, epics)
                : shortDescription.trim();
        String text = isBlank(description)
                ? descriptionOf(product, departmentName, services, fixVersion, schedule, raised, epics, stories)
                : description.trim();
        List<ChangeTask> tasks = services.stream().map(service -> taskOf(product, service, schedule)).toList();
        return new ProductionChange(null, null, product.id(), product.code(), product.name(), departmentName,
                fixVersion, schedule, summary, text, raised, epics.stream().map(JiraIssue::key).toList(),
                stories.stream().map(JiraIssue::key).toList(), tasks, null, null);
    }

    public ProductionChange numbered(String number, List<String> taskNumbers, String url) {
        List<ChangeTask> numberedTasks = IntStream.range(0, tasks.size())
                .mapToObj(index -> tasks.get(index).numbered(taskNumbers.get(index))).toList();
        return new ProductionChange(id, number, productId, productCode, productName, departmentName, fixVersion,
                schedule, shortDescription, description, template, epicKeys, storyKeys, numberedTasks, url,
                createdAt);
    }

    static String shortDescriptionOf(Product product, String fixVersion, List<JiraIssue> epics) {
        String release = product.name() + " " + fixVersion;
        String text = epics.isEmpty() ? release + " production release"
                : release + ": " + epics.stream().map(JiraIssue::summary).collect(joining("; "));
        return abbreviate(text, SHORT_DESCRIPTION_MAX);
    }

    static String descriptionOf(Product product, String departmentName, List<Service> services, String fixVersion,
                                ChangeSchedule schedule, ChangeTemplate template, List<JiraIssue> epics,
                                List<JiraIssue> stories) {
        String head = "Production release " + fixVersion + " of " + product.name() + " (" + product.code() + ")"
                + (departmentName == null ? "" : " in " + departmentName) + ".\n"
                + schedule.text() + ". "
                + (template.downtime() ? "Downtime expected during the installation." : "No downtime.") + "\n\n"
                + "Change tasks, one per service: "
                + services.stream().map(Service::name).collect(joining(", ")) + ".\n\n"
                + "Scope from Jira project " + template.jiraProjectKey() + ", FixVersion " + fixVersion + ":\n";
        String tail = "\n" + detailsOf(product, template);
        List<String> lines = new ArrayList<>();
        for (JiraIssue epic : epics) {
            lines.add(epic.line());
            stories.stream().filter(story -> epic.key().equals(story.epicKey()))
                    .map(story -> "- " + story.line()).forEach(lines::add);
        }
        StringBuilder text = new StringBuilder(head);
        int room = DESCRIPTION_MAX - bytes(head) - bytes(tail) - 60;
        for (int index = 0; index < lines.size(); index++) {
            int size = bytes(lines.get(index)) + 1;
            if (room - size < 0) {
                text.append("...and ").append(lines.size() - index).append(" more issues in Jira.\n");
                break;
            }
            room -= size;
            text.append(lines.get(index)).append('\n');
        }
        return abbreviate(text.append(tail).toString().strip(), DESCRIPTION_MAX);
    }

    private static String detailsOf(Product product, ChangeTemplate template) {
        Planning planning = template.planning();
        List<String> risks = template.riskAssessment().lines().stream().map(line -> abbreviate(line, SECTION_MAX))
                .toList();
        return section("Test summary", planning.testSummary())
                + section("Implementation plan", planning.implementationPlan())
                + section("Validation plan", planning.validationPlan())
                + section("Backout plan", planning.backoutPlan())
                + section("First use plan", planning.firstUsePlan())
                + abbreviate(template.privilegedAccess().text(), SECTION_MAX) + "\n\n"
                + "Risk assessment:\n" + (risks.isEmpty() ? "Not assessed." : String.join("\n", risks)) + "\n"
                + (template.description() == null ? ""
                : "\n" + section("About " + product.name(), template.description()));
    }

    private static String section(String title, String text) {
        return title + ":\n" + abbreviate(text, SECTION_MAX) + "\n\n";
    }

    static ChangeTask taskOf(Product product, Service service, ChangeSchedule schedule) {
        String how = service.settings().deployment().target() == DeployTarget.OPENSHIFT
                ? "Roll out its new image on OpenShift" : "Install it on the virtual machines with UrbanCode Deploy";
        String description = "Deploy " + service.name() + " of " + product.name()
                + (service.description() == null ? "" : " (" + service.description() + ")") + ", "
                + schedule.installationText() + ". " + how
                + ", then run its smoke tests and confirm the result in this task.";
        return new ChangeTask(null, service.name(),
                abbreviate("Deploy " + service.name() + " of " + product.name() + " to production",
                        SHORT_DESCRIPTION_MAX), abbreviate(description, DESCRIPTION_MAX));
    }
}
