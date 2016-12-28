/*
 * Copyright 2016-present Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.oiilab;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IpPrefix;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
@Component(immediate = true)
public class FatTreeApplication {

    private final Logger log = LoggerFactory.getLogger(getClass());

    //set static variables
    private static final int K = 4; // number for fat tree
    private static final int DEFAULT_TIMEOUT = 0;
    private static final int DEFAULT_PRIORITY_PREFIX = 100;
    private static final int DEFAULT_PRIORITY_SUFFIX = 10;
    private static final String MININET_IP = "192.168.0.15";
    private static final String MININET_SERVER_USER = "jkchoi";

    private static final String DEVICE_BASE = "of:0000000000%1$02d%2$02d%3$02d";
    private static final String IP_BASE = "10.%1$d.%2$d.%3$d/%4$d";

    private ApplicationId appId;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("net.oiilab.onos-fattree");

        // loop for pod
        for (int i = 0; i < K; i++) {
            // loop for switch number
            for (int j = 0; j < K; j++) {
                setSwitchFlowRules(i, j);
            }
        }
        setCoreSwitches(K);

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        flowRuleService.removeFlowRulesById(appId);
        log.info("Stopped");
    }

    /**
     * Install target switch flow rules.
     *
     * @param podNum pod number from 0 to (K - 1)
     * @param switchNum switch number from 0 to (K -1)
     */
    private void setSwitchFlowRules(int podNum, int switchNum) {

            // get deviceName
            String deviceName = String.format(DEVICE_BASE, podNum, switchNum, 1);
            // get device ID from device name
            DeviceId deviceId = DeviceId.deviceId(deviceName);
            String ipDst;

            // set prefix flow rule via NBI
            for (int j = 0; j < K; j++) {

                if (j < K / 2) {
                    // calculate prefix IP destination value
                    if (switchNum < K / 2) {
                        // set IP destination for edge switch
                        ipDst = String.format(IP_BASE, podNum, switchNum, 2 + j, 32);
                    } else {
                        // set IP destination for aggregation switch
                        ipDst = String.format(IP_BASE, podNum, j, 0, 24);
                    }
                    // install prefix flow rule
                    installFlowRule(deviceId, j + 1, ipDst, DEFAULT_PRIORITY_PREFIX);
                } else {
                    // set suffix flow rule via ovs command
                    // calculate suffix number
                    int index = j - (K / 2);
                    int suffixNumber = 2 + index;
                    int port = calculateSuffixPort(switchNum, index, K);
                    // install suffix flow rule
                    String bridgeName;
                    if (switchNum < K / 2) {
                        bridgeName = "e" + deviceName.substring(13);
                    } else {
                        bridgeName = "a" + deviceName.substring(13);
                    }


                    sendSuffixCommand(bridgeName, port, suffixNumber, DEFAULT_PRIORITY_SUFFIX, DEFAULT_TIMEOUT);

                }
            }
    }

    /**
     * Function for install flowrules to switch.
     * create traffic treatment by output port number
     * create selector builder by output IP dst matching
     *
     * @param deviceId target deviceId
     * @param outputPortNum output port number
     * @param ipDestination IP destination value, CIDR Record eg:"192.168.0.12/24"
     * @param priority flowrule priority
     */
    private void installFlowRule(DeviceId deviceId, int outputPortNum, String ipDestination,
                                 int priority) {

        // create traffic treatment
        TrafficTreatment trafficTreatment = DefaultTrafficTreatment.builder()
                .setOutput(PortNumber.portNumber(outputPortNum)).build();

        // create traffic selector
        TrafficSelector trafficSelector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPDst(IpPrefix.valueOf(ipDestination))
                .build();
        // create forwarding object(flow rule)
        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(trafficSelector)
                .withTreatment(trafficTreatment)
                .withPriority(priority)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makePermanent()
                .add();

        // install forwarding object to device
        flowObjectiveService.forward(deviceId, forwardingObjective);
    }

    /**
     * Function for install suffix flow rule to switch.
     * run ovs command via ssh
     *
     * @param bridgeName bridge name
     * @param port output port number
     * @param suffixNum suffix number
     * @param priority flowrule priority
     * @param timeout flowrule timeout
     */
    public void sendSuffixCommand(String bridgeName, int port, int suffixNum,
                                  int priority, int timeout) {

        String suffixIp = "0.0.0." + suffixNum;
        String suffixMask = "0.0.0.255";
        String separator = "/";
        String linkType =  "0x0800";
        // String command="sudo ovs-ofctl add-flow e000001 nw_dst=0.0.0.3/0.0.0.255,
        // priority=10,dl_type=0x0800,idle_timeout=3000,actions=output:4 --protocols=OpenFlow13";
        String command = "sudo ovs-ofctl add-flow %s nw_dst=%s,priority=%d," +
                "dl_type=%s,idle_timeout=%d,actions=output:%s --protocols=OpenFlow13";
        command = String.format(command, bridgeName, (suffixIp + separator + suffixMask),
                priority, linkType, timeout, port);
        String[] cmd = new String[]{"ssh", MININET_SERVER_USER + "@" + MININET_IP, command};
        // run command.
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            p.getErrorStream().close();
            p.getInputStream().close();
            p.getOutputStream().close();
            p.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Function for install flow rules to core switch.
     *
     * @param k pod number
     */
    private void setCoreSwitches(int k) {
        for (int i = 0; i < k / 2; i++) {
            for (int j = 0; j < k / 2; j++) {
                String deviceName = String.format(DEVICE_BASE, k, i + 1, j + 1);
                DeviceId deviceId = DeviceId.deviceId(deviceName);
                for (int z = 0; z < k; z++) {
                    // IP destination value
                    String ipDst = String.format(IP_BASE, z, 0, 0, 16);
                    installFlowRule(deviceId, z + 1, ipDst, DEFAULT_PRIORITY_PREFIX);
                }
            }
        }
    }

    /**
     * Calculate Suffix port number.
     *
     * @param switchNum switch number
     * @param index
     * @param k K value
     * @return port number
     */
    private int calculateSuffixPort(int switchNum, int index, int k) {
        return (switchNum + index) % (k / 2) + (k / 2) + 1;
    }
}
