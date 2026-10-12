package com.coco.subnet.service;

import com.coco.subnet.model.Cidr;
import com.coco.subnet.model.CidrResult;
import com.coco.subnet.model.SubnetInfo;
import org.springframework.stereotype.Service;

@Service
public class SubnetService {

    public CidrResult calculate(String cidrText) {
        Cidr cidr = Cidr.parse(cidrText, "cidr");
        return new CidrResult(SubnetInfo.of(cidr), cidr.correctionWarning().stream().toList());
    }
}
