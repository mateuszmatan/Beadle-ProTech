package com.bbh.itss.dso.portal.adapter.out.servicenow;

import com.bbh.itss.dso.portal.application.catalog.port.out.DepartmentRepositoryPort;
import com.bbh.itss.dso.portal.application.change.port.out.ChangeProductsPort;
import com.bbh.itss.dso.portal.application.change.port.out.ProTechLookupPort;
import com.bbh.itss.dso.portal.application.user.port.in.SignedInUserUseCase;
import com.bbh.itss.dso.portal.domain.catalog.Department;
import com.bbh.itss.dso.portal.domain.change.ChangeProduct;
import com.bbh.itss.dso.portal.domain.change.Lookup;
import com.bbh.itss.dso.portal.domain.change.LookupKind;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.GROUP_MAX;
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.NAME_MAX;
import static com.bbh.itss.dso.portal.domain.change.TaskDetails.RELEASE_MANAGEMENT;
import static com.bbh.itss.dso.portal.domain.shared.Text.abbreviateBytes;
import static com.bbh.itss.dso.portal.domain.shared.Text.bytes;
import static java.lang.Math.floorMod;
import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.util.Locale.ROOT;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.IntStream.range;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.trim;

@Component
@RequiredArgsConstructor
class DemoProTechLookups implements ProTechLookupPort {

    static final String SUPPORT = " Application Support";
    static final List<String> USERS = List.of("Aisha Rahman", "Ann Lee", "Charlotte Reed", "Daniel Foster",
            "Emma Brooks", "Grace Mitchell", "Grace Turner", "Hannah Whitfield", "Henry Collins", "James Carter",
            "Jane Smith", "Kenji Watanabe", "Laura Kingsley", "Marcus Webb", "Mateusz Matan", "Michael Grant",
            "Olivia Bennett", "Priya Natarajan", "Rebecca Lawson", "Robert Ellison", "Samuel Price", "Sophia Turner",
            "Thomas Ashby", "William Hayes");
    static final List<Lookup> DEPARTMENTS = List.of(new Lookup("AI Lab", "Cost centre 4110"),
            new Lookup("Capital Partners", "Cost centre 4220"), new Lookup("Compliance", "Cost centre 4630"),
            new Lookup("Corporate Technology", "Cost centre 4310"), new Lookup("Custody", "Cost centre 4410"),
            new Lookup("Fund Services", "Cost centre 4510"), new Lookup("Information Security", "Cost centre 4320"),
            new Lookup("Infrastructure & Operations", "Cost centre 4330"),
            new Lookup("Investor Services", "Cost centre 4520"), new Lookup("Private Banking", "Cost centre 4710"),
            new Lookup("Treasury", "Cost centre 4810"));
    static final List<Lookup> INFRASTRUCTURE_GROUPS = List.of(
            new Lookup("Cloud Engineering", "Infrastructure & Operations"),
            new Lookup("Data Movement - API", "Infrastructure & Operations"),
            new Lookup("Database Administration", "Infrastructure & Operations"),
            new Lookup("Middleware Support", "Infrastructure & Operations"),
            new Lookup("Network Operations", "Infrastructure & Operations"),
            new Lookup("OIS Support", "Infrastructure & Operations"),
            new Lookup("OpenShift Platform Support", "Infrastructure & Operations"),
            new Lookup(RELEASE_MANAGEMENT, "Corporate Technology"),
            new Lookup("Security Operations", "Information Security"),
            new Lookup("Service Desk", "Corporate Technology"));
    static final List<Lookup> INCIDENTS = List.of(
            new Lookup("INC0104211", "Login page times out for fund accountants"),
            new Lookup("INC0104377", "NAV report delivered late to clients"),
            new Lookup("INC0104502", "Payment gateway rejects SEPA instant payments"),
            new Lookup("INC0104618", "Reconciliation batch stops on a malformed custodian file"),
            new Lookup("INC0104733", "Corporate action elections not e-mailed"),
            new Lookup("INC0104890", "Investor portal shows an empty statement list"),
            new Lookup("INC0105014", "Access request stuck in approval"),
            new Lookup("INC0105126", "Certificate expiry alert not raised"),
            new Lookup("INC0105248", "Document extraction returns blank fields"),
            new Lookup("INC0105391", "Deal pipeline export to Excel fails"),
            new Lookup("INC0105407", "Slow search in the positions screen"),
            new Lookup("INC0105562", "Advisor assistant answers with outdated content"));
    static final List<Lookup> PROBLEMS = List.of(
            new Lookup("PRB0040112", "Connection pool exhausted at month end"),
            new Lookup("PRB0040187", "Time-outs calling the pricing service"),
            new Lookup("PRB0040255", "Batch jobs overlap with the backup window"),
            new Lookup("PRB0040319", "Expired TLS certificates on internal APIs"),
            new Lookup("PRB0040428", "Duplicate e-mails after a failover"),
            new Lookup("PRB0040506", "Stale reference data after the nightly load"),
            new Lookup("PRB0040611", "Memory leak in the document extraction workers"),
            new Lookup("PRB0040734", "Session loss behind the load balancer"));
    static final List<Lookup> CLIENTS = List.of(new Lookup("Alder Ridge Pension Fund", "Fund administration"),
            new Lookup("Aurora Global Equity Fund", "Fund administration"),
            new Lookup("Bluewater Insurance Group", "Custody"), new Lookup("Cedar Point Capital", "Private equity"),
            new Lookup("Copperfield Endowment", "Investor services"),
            new Lookup("Granite Peak Partners", "Private equity"),
            new Lookup("Harbor Lights Income Fund", "Fund administration"),
            new Lookup("Kestrel Sovereign Wealth Fund", "Custody"),
            new Lookup("Lakeshore Teachers Retirement System", "Custody"),
            new Lookup("Meridian Multi-Asset Trust", "Fund administration"),
            new Lookup("Northwind Family Office", "Private banking"),
            new Lookup("Oakmont University Endowment", "Investor services"),
            new Lookup("Pinecrest Infrastructure Fund", "Private equity"),
            new Lookup("Redwood ESG Leaders Fund", "Fund administration"),
            new Lookup("Silverline Asset Management", "Custody"),
            new Lookup("Summit Bay Credit Fund", "Fund administration"),
            new Lookup("Tidewater Charitable Trust", "Private banking"),
            new Lookup("Westbrook Emerging Markets Fund", "Fund administration"),
            new Lookup("Willow Creek Municipal Plan", "Custody"));

    private static final Map<String, String> COST_CENTRES = DEPARTMENTS.stream()
            .collect(toMap(Lookup::value, Lookup::detail, (first, second) -> first,
                    () -> new TreeMap<>(CASE_INSENSITIVE_ORDER)));

    private final ChangeProductsPort products;
    private final DepartmentRepositoryPort departments;
    private final SignedInUserUseCase users;

    @Override
    public List<Lookup> find(LookupKind kind, String query, int limit) {
        return lookups(kind).filter(lookup -> lookup.matches(query)).limit(limit).toList();
    }

    private Stream<Lookup> lookups(LookupKind kind) {
        return switch (kind) {
            case USERS -> once(Stream.concat(USERS.stream(), Stream.of(users.signedInUser().name())))
                    .map(name -> new Lookup(abbreviateBytes(name, GROUP_MAX), emailOf(name)));
            case DEPARTMENTS -> once(Stream.concat(departments.findAll().stream().map(Department::name),
                    COST_CENTRES.keySet().stream()))
                    .map(name -> new Lookup(abbreviateBytes(name, NAME_MAX), costCentreOf(name)));
            case ASSIGNMENT_GROUPS -> Stream.concat(catalogue().map(product -> new Lookup(
                    fitted(defaultIfBlank(trim(product.ownerTeam()), product.name()), SUPPORT, GROUP_MAX),
                    product.departmentName())), INFRASTRUCTURE_GROUPS.stream()).distinct();
            case RELEASES -> catalogue().flatMap(DemoProTechLookups::releasesOf);
            case CONFIGURATION_ITEMS -> catalogue().map(product -> new Lookup(
                    abbreviateBytes(product.name(), GROUP_MAX),
                    defaultIfBlank(trim(product.ownerTeam()), product.departmentName())));
            case INCIDENTS -> INCIDENTS.stream();
            case PROBLEMS -> PROBLEMS.stream();
            case CLIENTS -> CLIENTS.stream();
        };
    }

    private Stream<ChangeProduct> catalogue() {
        return products.findAll().stream();
    }

    private static Stream<String> once(Stream<String> names) {
        return names.filter(StringUtils::isNotBlank).map(String::trim)
                .collect(toCollection(() -> new TreeSet<>(CASE_INSENSITIVE_ORDER))).stream();
    }

    private static String costCentreOf(String department) {
        return COST_CENTRES.getOrDefault(department,
                "Cost centre %d".formatted(4900 + floorMod(department.toLowerCase(ROOT).hashCode(), 100)));
    }

    private static String fitted(String head, String tail, int maxBytes) {
        return abbreviateBytes(head, maxBytes - bytes(tail)) + tail;
    }

    private static Stream<Lookup> releasesOf(ChangeProduct product) {
        Random random = new Random(product.code().hashCode());
        int major = 1 + random.nextInt(5);
        int minor = random.nextInt(4);
        return range(minor, minor + 3).mapToObj(next -> new Lookup(
                fitted(product.name(), " " + major + "." + next, NAME_MAX), product.code()));
    }

    private static String emailOf(String name) {
        return name.toLowerCase(ROOT).replaceAll("[^a-z ]", "").replace(' ', '.') + "@bbh.com";
    }
}
