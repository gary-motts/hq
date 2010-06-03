/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 * 
 * Copyright (C) [2004-2010], Hyperic, Inc.
 * This file is part of HQ.
 * 
 * HQ is free software; you can redistribute it and/or modify
 * it under the terms version 2 of the GNU General Public License as
 * published by the Free Software Foundation. This program is distributed
 * in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA.
 */

package org.hyperic.hq.plugin.vsphere;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.authz.shared.AuthzConstants;
import org.hyperic.hq.hqapi1.AgentApi;
import org.hyperic.hq.hqapi1.HQApi;
import org.hyperic.hq.hqapi1.ResourceApi;
import org.hyperic.hq.hqapi1.ResourceEdgeApi;
import org.hyperic.hq.hqapi1.XmlUtil;
import org.hyperic.hq.hqapi1.types.Agent;
import org.hyperic.hq.hqapi1.types.AgentResponse;
import org.hyperic.hq.hqapi1.types.AgentsResponse;
import org.hyperic.hq.hqapi1.types.Resource;
import org.hyperic.hq.hqapi1.types.ResourceConfig;
import org.hyperic.hq.hqapi1.types.ResourceEdge;
import org.hyperic.hq.hqapi1.types.ResourceFrom;
import org.hyperic.hq.hqapi1.types.ResourceProperty;
import org.hyperic.hq.hqapi1.types.ResourcePrototype;
import org.hyperic.hq.hqapi1.types.ResourcePrototypeResponse;
import org.hyperic.hq.hqapi1.types.ResourceTo;
import org.hyperic.hq.hqapi1.types.ResourcesResponse;
import org.hyperic.hq.hqapi1.types.Response;
import org.hyperic.hq.hqapi1.types.ResponseStatus;
import org.hyperic.hq.hqapi1.types.StatusResponse;
import org.hyperic.hq.product.PluginException;
import org.hyperic.hq.product.ProductPlugin;
import org.hyperic.util.config.ConfigResponse;

import com.vmware.vim25.AboutInfo;
import com.vmware.vim25.GuestInfo;
import com.vmware.vim25.GuestNicInfo;
import com.vmware.vim25.HostConfigInfo;
import com.vmware.vim25.HostHardwareSummary;
import com.vmware.vim25.HostIpConfig;
import com.vmware.vim25.HostNetworkInfo;
import com.vmware.vim25.HostVirtualNic;
import com.vmware.vim25.HostVirtualNicSpec;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ToolsConfigInfo;
import com.vmware.vim25.VirtualHardware;
import com.vmware.vim25.VirtualMachineConfigInfo;
import com.vmware.vim25.VirtualMachineFileInfo;
import com.vmware.vim25.VirtualMachineRuntimeInfo;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.ResourcePool;
import com.vmware.vim25.mo.VirtualMachine;

/**
 * HQApi based auto-discovery for vSphere Host and VM platform types. 
 */
public class VCenterPlatformDetector {

    //duplicating these constants as our build only depends on the pdk
    private static final String HQ_IP = "agent.setup.camIP";
    private static final String HQ_PORT = "agent.setup.camPort";
    private static final String HQ_SPORT = "agent.setup.camSSLPort";
    private static final String HQ_SSL = "agent.setup.camSecure";
    private static final String HQ_USER = "agent.setup.camLogin";
    private static final String HQ_PASS = "agent.setup.camPword";
    private static final String AGENT_IP = "agent.setup.agentIP";
    private static final String AGENT_PORT = "agent.setup.agentPort";

    private static final String VC_TYPE = "VMware vCenter";
    private static final String VM_TYPE = AuthzConstants.platformPrototypeVmwareVsphereVm;
    private static final String HOST_TYPE = "VMware vSphere Host";
    private static final String POOL_TYPE = "VMware vSphere Resource Pool";
    private static final String DEFAULT_POOL = "Resources";

    private static final Log log =
        LogFactory.getLog(VCenterPlatformDetector.class.getName());
    private static final boolean isDump =
        "true".equals(System.getProperty("vsphere.dump"));

    private Properties props;

    public VCenterPlatformDetector(Properties props) {
        this.props = props;
    }

    //XXX future HQ/pdk should provide this.
    private HQApi getApi() {
        boolean isSecure;
        String scheme;
        String host = this.props.getProperty(HQ_IP, "localhost");
        String port;
        if ("yes".equals(this.props.getProperty(HQ_SSL))) {
            isSecure = true;
            port = this.props.getProperty(HQ_SPORT, "7443");
            scheme = "https";
        }
        else {
            isSecure = false;
            port = this.props.getProperty(HQ_PORT, "7080");
            scheme = "http";
        }
        String user = this.props.getProperty(HQ_USER, "hqadmin");
        String pass = this.props.getProperty(HQ_PASS, "hqadmin");

        HQApi api = new HQApi(host, Integer.parseInt(port), isSecure, user, pass);
        log.debug("Using HQApi at " + scheme + "://" + host + ":" + port);
        return api;
    }

    private void assertSuccess(Response response, String msg, boolean abort)
        throws PluginException {

        if (ResponseStatus.SUCCESS.equals(response.getStatus())) {
            return;
        }
        String reason;
        if (response.getError() == null) {
            reason = "unknown";
        }
        else {
            reason = response.getError().getReasonText();
        }
        msg += ": " + reason;
        if (abort) {
            throw new PluginException(msg);
        }
        else {
            log.error(msg);
        }
    }

    private ResourceApi getResourceApi() {
        return getApi().getResourceApi();   
    }

    private Agent getAgent()
        throws IOException, PluginException {

        AgentApi api = getApi().getAgentApi();
        String host = this.props.getProperty(AGENT_IP);
        String port = this.props.getProperty(AGENT_PORT, "2144");

        if (host != null) {
            String msg = "getAgent(" + host + "," + port + ")";
            AgentResponse response = api.getAgent(host, Integer.parseInt(port));
            assertSuccess(response, msg, true);
            log.debug(msg + ": ok");
            return response.getAgent();
        }
        else { //XXX try harder to find the agent running this plugin.
            AgentsResponse response = api.getAgents();
            assertSuccess(response, "getAgents()", true);
            List<Agent> agents = response.getAgent();
            if (agents.size() == 0) {
                throw new PluginException("No agents available");
            }
            Agent agent = agents.get(0);
            log.debug("Using agent: " + agent.getAddress() + ":" + agent.getPort());
            return agents.get(0);
        }
    }

    private void dump(List<Resource> resources) {
        ResourcesResponse rr = new ResourcesResponse();
        rr.getResource().addAll(resources);
        try {
            XmlUtil.serialize(rr, System.out, Boolean.TRUE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //XXX might want to store these in memory rather than
    //the metric template, should any of the props change.
    private void mergeVSphereConfig(VSphereResource platform) {
        String[] vprops = {
            VSphereUtil.PROP_URL,
            VSphereUtil.PROP_USERNAME,
            VSphereUtil.PROP_PASSWORD
        };
        for (int i=0; i<vprops.length; i++) {
            String val = this.props.getProperty(vprops[i]);
            if (val != null) {
                platform.addConfig(vprops[i], val);
            }
        }
    }

    private ResourcePrototype getResourceType(String name)
        throws IOException, PluginException {

        ResourceApi api = getResourceApi();
        ResourcePrototypeResponse rpr =
            api.getResourcePrototype(name);
        assertSuccess(rpr, "getResourcePrototype(" + name + ")", true);
        ResourcePrototype type = rpr.getResourcePrototype();
        log.debug("'" + name + "' id=" + type.getId());
        return type;
    }

    private VSphereResource discoverVM(VirtualMachine vm)
        throws Exception {

        VirtualMachineConfigInfo info = vm.getConfig();

        if (info.isTemplate()) {
            return null; //filter out template VMs
        }

        VirtualMachineRuntimeInfo runtime = vm.getRuntime();
        GuestInfo guest = vm.getGuest();
        ResourcePool pool = vm.getResourcePool();

        VSphereResource platform = new VSphereResource();
        VirtualMachineFileInfo files = info.getFiles();
        platform.setName(info.getName());
        platform.setFqdn(info.getUuid());
        platform.setDescription(info.getGuestFullName());

        ConfigResponse config = new ConfigResponse();
        config.setValue(VSphereVmCollector.PROP_VM, info.getName());
        platform.addConfig(config);
        //ConfigInfo
        ConfigResponse cprops = new ConfigResponse();
        cprops.setValue(ProductPlugin.PROP_INSTALLPATH, files.getVmPathName());
        cprops.setValue("guestOS", info.getGuestFullName());
        cprops.setValue("version", info.getVersion());
        //HardwareInfo
        VirtualHardware hw = info.getHardware();
        cprops.setValue("numvcpus", hw.getNumCPU());
        cprops.setValue("memsize", hw.getMemoryMB());
        //ToolsInfo
        ToolsConfigInfo tools = info.getTools();
        Integer toolsVersion = tools.getToolsVersion();
        if (toolsVersion != null) {
            cprops.setValue("toolsVersion", toolsVersion.toString());
        }
        //PoolInfo
        cprops.setValue("pool", (String)pool.getPropertyByPath("name"));

        String state = runtime.getPowerState().toString();
        if (state.equals("poweredOn")) {
            String name;
            if ((name = guest.getHostName()) != null) {
                cprops.setValue("hostName", name);
            }
            //NetInfo
            GuestNicInfo[] nics = guest.getNet();
            if (nics != null) {
                for (int i=0; i<nics.length; i++) {
                    String mac = nics[i].getMacAddress();
                    if (mac.equals("00:00:00:00:00:00")) {
                        log.info("UUID=" + info.getUuid() +
                                 " and NIC=" + nics[i].getIpAddress() +
                                 " has macaddr=" + mac + 
                                 ".  Ignoring entire platform since macaddr is invalid.  " +
                                 "Platform will be picked up when the macaddr is valid");
                        return null;
                    }
                    String[] ips = nics[i].getIpAddress();
                    if ((mac != null) && (ips != null) && (ips.length != 0)) {
                        cprops.setValue("macAddress", mac);
                        cprops.setValue("ip", ips[0]);
                        platform.addIp(ips[0], "", mac);
                    }
                }
            }
        }
        else {
            log.info(info.getName() + " powerState=" + state);
            return null;
        }

        ManagedObjectReference hmor = runtime.getHost();
        if (hmor != null) {
            HostSystem host = new HostSystem(vm.getServerConnection(), hmor);
            cprops.setValue("esxHost", host.getName());
        }

        platform.addProperties(cprops);
        log.debug("discovered " + VM_TYPE + ": " + vm.getName());
        return platform;
    }

    private VSphereHostResource discoverHost(HostSystem host)
        throws Exception {

        HostConfigInfo info = host.getConfig();
        HostNetworkInfo netinfo = info.getNetwork();
        AboutInfo about = info.getProduct();
        String address = null;
        VSphereHostResource platform = new VSphereHostResource();

        ConfigResponse cprops = new ConfigResponse();
        platform.setName(host.getName());
        platform.setDescription(about.getFullName());
        
        if (netinfo.getVnic() == null) {
            try {
                // Host name may be the IP address
                InetAddress inet = InetAddress.getByName(platform.getName());
                address = inet.getHostAddress();
            } catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.debug(platform.getName() + " does not have an IP address", e);
                }
            }
        } else {
            for (HostVirtualNic nic : netinfo.getVnic()) {
                HostVirtualNicSpec spec = nic.getSpec();
                HostIpConfig ip = spec.getIp();
                platform.addIp(ip.getIpAddress(), ip.getSubnetMask(), spec.getMac());
                if (address == null) {
                    address = ip.getIpAddress();
                }
            }
        }

        cprops.setValue("version", about.getVersion());
        cprops.setValue("build", about.getBuild());
        if (address != null) {
            cprops.setValue("ip", address);
        }
        cprops.setValue("defaultGateway", netinfo.getIpRouteConfig().getDefaultGateway());
        String[] dns = netinfo.getDnsConfig().getAddress();
        String[] dnsProps = { "primaryDNS", "secondaryDNS" };
        for (int i=0; i<dnsProps.length; i++) {
            if (i >= dns.length) {
                break;
            }
            cprops.setValue(dnsProps[i], dns[i]);
        }

        HostHardwareSummary hw = host.getSummary().getHardware();
        platform.setFqdn(hw.getUuid());
        cprops.setValue("hwVendor", hw.getVendor());
        cprops.setValue("hwModel", hw.getModel());
        cprops.setValue("hwCpu", hw.getCpuModel());
        cprops.setValue("hwSockets", String.valueOf(hw.getNumCpuPkgs()));
        cprops.setValue("hwCores", String.valueOf(hw.getNumCpuCores() / hw.getNumCpuPkgs()));

        ManagedEntity mor = host.getParent();
        String prev = null;
        while (true) {
            if (mor.getName().equals("Datacenters")) {
                cprops.setValue("parent", prev); //Data Center
            }
            else {
                prev = mor.getName();
            }

            if ((mor = mor.getParent()) == null) {
                break;
            }
        }

        platform.addProperties(cprops);
        platform.addConfig(VSphereUtil.PROP_HOSTNAME, host.getName());

        return platform;
    }

    private List<Resource> discoverHosts(VSphereUtil vim, Agent agent)
        throws IOException, PluginException {

        List<Resource> resources = new ArrayList<Resource>();
        ResourcePrototype hostType = getResourceType(HOST_TYPE);
        ResourcePrototype vmType = getResourceType(VM_TYPE);

        try {
            ManagedEntity[] hosts = vim.find(VSphereUtil.HOST_SYSTEM);

            for (int i=0; i<hosts.length; i++) {
                if (! (hosts[i] instanceof HostSystem)) {
                    log.debug(hosts[i] + " not a HostSystem, type=" +
                              hosts[i].getMOR().getType());
                    continue;
                }

                HostSystem host = (HostSystem)hosts[i];
                try {
                    VSphereHostResource platform = discoverHost(host);
                    if (platform == null) {
                        continue;
                    }
                    platform.setResourcePrototype(hostType);
                    platform.setAgent(agent);
                    mergeVSphereConfig(platform);
                    
                    VirtualMachine[] hostVms = host.getVms();
                    for (int v=0; v<hostVms.length; v++) {
                        VSphereResource vm = discoverVM(hostVms[v]);
                        if (vm != null) {
                            vm.setResourcePrototype(vmType);
                            vm.setAgent(agent);
                            mergeVSphereConfig(vm);
                            platform.getVirtualMachines().add(vm);
                        }
                    }
                    
                    resources.add(platform);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        return resources;
    }

    public void discoverPlatforms()
        throws IOException, PluginException {
        VSphereUtil vim = null;
        vim = VSphereUtil.getInstance(this.props);

        try {
            Agent agent = getAgent();
            List<Resource> hosts = discoverHosts(vim, agent);
            List<Resource> vms = new ArrayList<Resource>();
            Map<String, List<Resource>> hostVmMap = new HashMap<String, List<Resource>>();

            for (Resource r : hosts) {
                VSphereHostResource h = (VSphereHostResource) r;
                vms.addAll(h.getVirtualMachines());
                hostVmMap.put(r.getName(), h.getVirtualMachines());
            }
            
            if (isDump) {
                dump(vms);
                dump(hosts);
            }
            else {
                ResourceApi api = getApi().getResourceApi();

                StatusResponse response;
                response = api.syncResources(vms);
                assertSuccess(response, "sync " + vms.size() + " VMs", false);
                response = api.syncResources(hosts);
                assertSuccess(response, "sync " + hosts.size() + " Hosts", false);
                
                syncResourceEdges(vim, hostVmMap);
            }
        } finally {
            VSphereUtil.dispose(vim);
        }
    }
    
    private void syncResourceEdges(VSphereUtil vim,
                                   Map<String, List<Resource>> vcHostVmMap) 
        throws IOException, PluginException {
        ResourceApi rApi = getApi().getResourceApi();
        ResourceEdgeApi reApi = getApi().getResourceEdgeApi();

        List<ResourceEdge> edges = new ArrayList<ResourceEdge>();

        Resource vCenter = null;
        ResourcePrototype vcType = getResourceType(VC_TYPE);
        ResourcesResponse vcResponse = rApi.getResources(vcType, true, false);
        assertSuccess(vcResponse, "Getting all " + VC_TYPE, false);
        
        for (Resource r : vcResponse.getResource()) {
            for (ResourceConfig c : r.getResourceConfig()) {                
                if (VSphereUtil.PROP_URL.equals(c.getKey())) {
                    if (c.getValue().equals(VSphereUtil.getURL(this.props))) {
                        vCenter = r;
                        break;
                    }
                }
            }
        }

        if (vCenter == null) {
            if (log.isDebugEnabled()) {
                log.debug("No VMware vCenter server found with url=" 
                            + VSphereUtil.getURL(this.props));
            }
            return;
        }
        
        ResourcePrototype hostType = getResourceType(HOST_TYPE);
        ResourcesResponse hostResponse = rApi.getResources(hostType, true, false);
        assertSuccess(hostResponse, "Getting all " + HOST_TYPE, false);

        ResourceEdge edge = new ResourceEdge();
        ResourceFrom from = new ResourceFrom();
        ResourceTo to = new ResourceTo();
        Map<String, Resource> hqHostResourceMap = new HashMap<String, Resource>();

        for (Resource r : hostResponse.getResource()) {            
            for (ResourceConfig c : r.getResourceConfig()) {                
                if (VSphereUtil.PROP_URL.equals(c.getKey())) {
                    if (c.getValue().equals(VSphereUtil.getURL(this.props))) {
                        to.getResource().add(r);
                        hqHostResourceMap.put(r.getName(), r);
                    }
                }
            }            
        }
        
        if (log.isDebugEnabled()) {
            log.debug("vc name=" + vCenter.getName()
                        + ", resourceId=" + vCenter.getId()
                        + ", host size=" + to.getResource().size());
        }

        from.setResource(vCenter);
        edge.setRelation("virtual");
        edge.setResourceFrom(from);
        edge.setResourceTo(to);
        edges.add(edge);

        StatusResponse syncResponse = reApi.syncResourceEdges(edges);
        assertSuccess(syncResponse, "Sync vCenter and host edges", false);
        
        ResourcePrototype vmType = getResourceType(VM_TYPE);
        ResourcesResponse vmResponse = rApi.getResources(vmType, true, false);
        assertSuccess(vmResponse, "Getting all " + VM_TYPE, false);
        
        Map<String, List<Resource>> hqHostVmMap = new HashMap<String, List<Resource>>();

        for (Resource r : vmResponse.getResource()) {
            String esxHost = null;
            for (ResourceProperty p : r.getResourceProperty()) {
                if ("esxHost".equals(p.getKey())) {
                    esxHost = p.getValue();
                    break;
                }
            }
            List<Resource> vmResources = hqHostVmMap.get(esxHost);
            if (vmResources == null) {
                vmResources = new ArrayList<Resource>();
                hqHostVmMap.put(esxHost, vmResources);
            }
            vmResources.add(r);
        }
        
        edges.clear();
        
        for (Resource r : hostResponse.getResource()) {            
            ResourceFrom parent = new ResourceFrom();
            parent.setResource(r);
            
            ResourceTo children = new ResourceTo();
            List<Resource> vmResources = hqHostVmMap.get(r.getName());
            if (vmResources != null) {
                children.getResource().addAll(vmResources);
            }

            ResourceEdge rEdge = new ResourceEdge();
            rEdge.setRelation("virtual");
            rEdge.setResourceFrom(parent);
            rEdge.setResourceTo(children);
            edges.add(rEdge);
            
            if (log.isDebugEnabled()) {
                log.debug("host name=" + r.getName()
                            + ", resourceId=" + r.getId()
                            + ", vm size=" + children.getResource().size());
            }
        }
        
        syncResponse = reApi.syncResourceEdges(edges);
        assertSuccess(syncResponse, "Sync host and VM edges", false);
        
        // delete resouces that have been manually removed from vCenter
        for (Iterator it=hqHostVmMap.keySet().iterator(); it.hasNext();) {
            String hostName = (String)it.next();
            List<Resource> hqVms = hqHostVmMap.get(hostName);
            List<Resource> vcVms = vcHostVmMap.get(hostName);
            
            if (vcVms == null) {
                // not one of the hosts in vCenter
                Resource r = hqHostResourceMap.get(hostName);
                if (r != null) {
                    removeHost(vim, r);
                }
            } else {
                List<String> vcVmNames = new ArrayList<String>();
                for (Resource r : vcVms) {
                    vcVmNames.add(r.getName());
                }
                
                for (Resource r : hqVms) {
                    if (!vcVmNames.contains(r.getName())) {
                        // Not one of the powered-on VMs from vCenter
                        removeVM(vim, r);
                    }
                }
            }
        }
    }
            
    private void removeHost(VSphereUtil vim, Resource r)
        throws IOException, PluginException {
        
        try {
            // verify to see if it exists in vCenter
            HostSystem hs =
                (HostSystem)vim.find(VSphereUtil.HOST_SYSTEM, r.getName());
        } catch (ManagedEntityNotFoundException me) {
            removeResource(r);
        }
    }

    private void removeVM(VSphereUtil vim, Resource r)
        throws IOException, PluginException {
    
        try {
            // verify to see if it exists in vCenter
            VirtualMachine vm =
                (VirtualMachine)vim.find(VSphereUtil.VM, r.getName());
        } catch (ManagedEntityNotFoundException me) {
            removeResource(r);
        }
    }
    
    private void removeResource(Resource r) 
        throws IOException, PluginException {

        if (log.isDebugEnabled()) {
            log.debug("Managed entity (" + r.getName() + ") no longer exists in vCenter. "
                         + " Removing from HQ inventory.");
        }
        
        // throttle requests to the hq server to minimize StaleStateExceptions
        // TODO: there needs to be a better way to do this 
        try {
            Thread.sleep(2500);
        } catch (InterruptedException e) {
            // Ignore
        }

        ResourceApi rApi = getApi().getResourceApi();

        // TODO: As a final step, need to check resource availability
        // (must be DOWN) before deleting.
        
        StatusResponse deleteResponse = rApi.deleteResource(r.getId());
        assertSuccess(deleteResponse, "Delete resource id=" + r.getId(), false);
    }
}
