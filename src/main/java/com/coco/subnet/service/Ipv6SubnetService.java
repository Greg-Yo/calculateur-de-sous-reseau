package com.coco.subnet.service;

import com.coco.subnet.model.Ipv6Cidr;
import com.coco.subnet.model.Ipv6CidrResult;
import com.coco.subnet.model.Ipv6SubnetInfo;
import org.springframework.stereotype.Service;

@Service
public class Ipv6SubnetService {

    public Ipv6CidrResult calculate(String cidrText) {
        Ipv6Cidr cidr = Ipv6Cidr.parse(cidrText, "cidr");
        return new Ipv6CidrResult(Ipv6SubnetInfo.of(cidr), cidr.correctionWarning().stream().toList());
    }
}
