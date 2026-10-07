package com.bbh.itss.dso.portal.domain.change;

import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.catalog.Service;
import lombok.Builder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.bbh.itss.dso.portal.domain.catalog.DeployTarget.OPENSHIFT;
import static com.bbh.itss.dso.portal.domain.shared.Text.abbreviateBytes;
import static com.bbh.itss.dso.portal.domain.shared.Text.bytes;
import static java.util.stream.Collectors.joining;
import static java.util.stream.IntStream.range;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Builder(toBuilder = true)
public record ProductionChange(Long id, String number, Long productId, String productCode, String productName,
                               String departmentName, ChangeWindow window, String shortDescription,
                               String description, ChangeTemplate template, List<String> epicKeys,
                               List<String> storyKeys, List<ChangeTask> tasks, String url, Instant createdAt) {

    public static final int SHORT_DESCRIPTION_MAX = 160;
    public static final int DESCRIPTION_MAX = 4000;

    public ProductionChange {
        epicKeys = List.copyOf(epicKeys);
        storyKeys = List.copyOf(storyKeys);
        tasks = List.copyOf(tasks);
    }

    public static ProductionChange draft(Product product, String departmentName, List<Service> services,
                                         ChangeTemplate template, ChangeWindow window, List<JiraIssue> epics,
                                         List<JiraIssue> stories, String shortDescription, String description) {
        String summary = isBlank(shortDescription) ? shortDescriptionOf(product, epics) : shortDescription.trim();
        String text = isBlank(description)
                ? descriptionOf(product, departmentName, services, template, window, epics, stories)
                : description.trim();
        List<ChangeTask> tasks = services.stream().map(service -> taskOf(product, service, window)).toList();
        return builder().productId(product.id()).productCode(product.code()).productName(product.name())
                .departmentName(departmentName).window(window).shortDescription(summary).description(text)
                .template(template).epicKeys(epics.stream().map(JiraIssue::key).toList())
                .storyKeys(stories.stream().map(JiraIssue::key).toList()).tasks(tasks).build();
    }

    public ProductionChange numbered(String number, List<String> taskNumbers, String url) {
        List<ChangeTask> numberedTasks = range(0, tasks.size())
                .mapToObj(index -> tasks.get(index).numbered(taskNumbers.get(index))).toList();
        return toBuilder().number(number).tasks(numberedTasks).url(url).build();
    }

    static String shortDescriptionOf(Product product, List<JiraIssue> epics) {
        String text = epics.isEmpty() ? product.name() + " production release"
                : product.name() + " release: " + epics.stream().map(JiraIssue::summary).collect(joining("; "));
        return abbreviateBytes(text, SHORT_DESCRIPTION_MAX);
    }

    static String descriptionOf(Product product, String departmentName, List<Service> services,
                                ChangeTemplate template, ChangeWindow window, List<JiraIssue> epics,
                                List<JiraIssue> stories) {
        String head = "Production release of " + product.name() + " (" + product.code() + ")"
                + (departmentName == null ? "" : " in " + departmentName) + ", " + window.text() + ".\n\n"
                + "Change tasks, one per service: "
                + services.stream().map(Service::name).collect(joining(", ")) + ".\n\n"
                + "Scope from Jira project " + template.jiraProjectKey() + ":\n";
        String tail = template.description() == null ? ""
                : "\nAbout " + product.name() + ":\n" + template.description() + "\n";
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
        return abbreviateBytes(text.append(tail).toString().strip(), DESCRIPTION_MAX);
    }

    static ChangeTask taskOf(Product product, Service service, ChangeWindow window) {
        String how = service.settings().deployment().target() == OPENSHIFT
                ? "Roll out its new image on OpenShift" : "Install it on the virtual machines with UrbanCode Deploy";
        String description = "Deploy " + service.name() + " of " + product.name()
                + (service.description() == null ? "" : " (" + service.description() + ")") + ", "
                + window.text() + ". " + how + ", then run its smoke tests and confirm the result in this task.";
        return new ChangeTask(null, service.name(),
                abbreviateBytes("Deploy " + service.name() + " of " + product.name() + " to production",
                        SHORT_DESCRIPTION_MAX), abbreviateBytes(description, DESCRIPTION_MAX));
    }
}
