package com.bbh.itss.beadle.protech.adapter.out.protech;

import com.bbh.itss.beadle.protech.application.change.port.out.ProTechLookupPort;
import com.bbh.itss.beadle.protech.domain.change.Lookup;
import com.bbh.itss.beadle.protech.domain.change.LookupKind;
import com.bbh.itss.beadle.protech.domain.change.ProTechMode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static com.bbh.itss.beadle.protech.domain.change.TaskDetails.RELEASE_MANAGEMENT;
import static java.util.Locale.ROOT;
import static java.util.stream.IntStream.range;

/**
 * The lookups of the demo ProTech: a fixed catalogue of BBH people, departments, products with their support groups,
 * releases and configuration items, infrastructure groups, incidents, problems and clients.
 */
@Component
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
    static final List<Product> CATALOGUE = List.of(
            new Product("DOCSENSE", "DocSense", "AI Lab", "AI Lab Engineering"),
            new Product("ADVISORAI", "Advisor Assistant", "AI Lab", "AI Lab Engineering"),
            new Product("DEALFLOW", "DealFlow", "Capital Partners", "Private Equity Technology"),
            new Product("LPPORTAL", "LP Portal", "Capital Partners", "Investor Reporting"),
            new Product("ACCESSHUB", "Access Hub", "Corporate Technology", "Identity and Access"),
            new Product("CERTSCANNER", "CertScanner", "Corporate Technology", "Technology Architecture"),
            new Product("SAFEKEEP", "Safekeeping Ledger", "Custody", "Custody Platform"),
            new Product("CORPACT", "Corporate Actions", "Custody", "Asset Servicing"),
            new Product("PAYHUB", "Payments Hub", "Fund Services", "Payments Engineering"),
            new Product("NAVCALC", "NAV Calculator", "Fund Services", "Fund Accounting"));
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

    @Override
    public boolean connected() {
        return false;
    }

    @Override
    public ProTechMode mode() {
        return ProTechMode.DEMO;
    }

    @Override
    public List<Lookup> find(LookupKind kind, String query, int limit) {
        return lookups(kind).filter(lookup -> lookup.matches(query)).limit(limit).toList();
    }

    private static Stream<Lookup> lookups(LookupKind kind) {
        return switch (kind) {
            case USERS -> USERS.stream().map(name -> new Lookup(name, emailOf(name)));
            case DEPARTMENTS -> DEPARTMENTS.stream();
            case ASSIGNMENT_GROUPS -> Stream.concat(CATALOGUE.stream()
                    .map(product -> new Lookup(product.ownerTeam() + SUPPORT, product.department())),
                    INFRASTRUCTURE_GROUPS.stream()).distinct();
            case RELEASES -> CATALOGUE.stream().flatMap(DemoProTechLookups::releasesOf);
            case CONFIGURATION_ITEMS -> CATALOGUE.stream()
                    .map(product -> new Lookup(product.name(), product.ownerTeam()));
            case INCIDENTS -> INCIDENTS.stream();
            case PROBLEMS -> PROBLEMS.stream();
            case CLIENTS -> CLIENTS.stream();
        };
    }

    private static Stream<Lookup> releasesOf(Product product) {
        Random random = new Random(product.code().hashCode());
        int major = 1 + random.nextInt(5);
        int minor = random.nextInt(4);
        return range(minor, minor + 3)
                .mapToObj(next -> new Lookup(product.name() + " " + major + "." + next, product.code()));
    }

    private static String emailOf(String name) {
        return name.toLowerCase(ROOT).replaceAll("[^a-z ]", "").replace(' ', '.') + "@bbh.com";
    }

    record Product(String code, String name, String department, String ownerTeam) {
    }
}
