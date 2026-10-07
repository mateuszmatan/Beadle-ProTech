package com.bbh.itss.dso.portal.adapter.out.servicenow

import com.bbh.itss.dso.portal.domain.change.ChangeTask
import com.bbh.itss.dso.portal.domain.change.ProductionChange
import spock.lang.Specification

import static com.bbh.itss.dso.portal.support.ChangeFixtures.FIX_VERSION
import static com.bbh.itss.dso.portal.support.ChangeFixtures.schedule
import static com.bbh.itss.dso.portal.support.ChangeFixtures.template

class DemoServiceNowAdapterSpec extends Specification {

    def serviceNow = new DemoServiceNowAdapter()

    def "each raised change gets a new CHG number and a new CTASK number per task"() {
        when:
        def first = serviceNow.raise(change(['gui', 'api']))
        def second = serviceNow.raise(change(['batch']))

        then:
        !serviceNow.connected()
        first.number() ==~ /CHG\d{7}/
        first.taskNumbers().size() == 2
        first.taskNumbers().every { it ==~ /CTASK\d{7}/ }
        first.url() == null
        second.number() != first.number()
        second.taskNumbers().size() == 1
        !first.taskNumbers().contains(second.taskNumbers()[0])
    }

    static ProductionChange change(List<String> services) {
        new ProductionChange(null, null, 1L, 'CERT', 'CertScanner', null, FIX_VERSION, schedule(), 'Release',
                'Release', template(), ['CERT-1'], [], services.collect { new ChangeTask(null, it, 'Deploy', 'Deploy') },
                null, null)
    }
}
