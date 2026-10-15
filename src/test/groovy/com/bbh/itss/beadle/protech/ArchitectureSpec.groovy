package com.bbh.itss.beadle.protech

import com.bbh.itss.beadle.protech.support.ArchitectureSpecification

class ArchitectureSpec extends ArchitectureSpecification {

    @Override
    Class<?> application() {
        BeadleProTechApplication
    }

    @Override
    String httpAdapter() {
        'com.bbh.itss.beadle.protech.adapter.out.servicenow..'
    }
}
