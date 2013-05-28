/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.transport.http.netty.server;


import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.annotation.Resource;
import org.apache.cxf.Bus;
import org.apache.cxf.buslifecycle.BusLifeCycleListener;
import org.apache.cxf.buslifecycle.BusLifeCycleManager;
import org.apache.cxf.common.logging.LogUtils;

public class NettyHttpServerEngineFactory implements BusLifeCycleListener {
    private static final Logger LOG =
            LogUtils.getL7dLogger(NettyHttpServerEngineFactory.class);

    private static ConcurrentHashMap<Integer, NettyHttpServerEngine> portMap =
            new ConcurrentHashMap<Integer, NettyHttpServerEngine>();

    private Bus bus;

    private BusLifeCycleManager lifeCycleManager;

    public NettyHttpServerEngineFactory() {
        // Empty
    }

    public NettyHttpServerEngineFactory(Bus b) {
        setBus(b);
    }

    public Bus getBus() {
        return bus;
    }

    /**
     * This call is used to set the bus. It should only be called once.
     *
     * @param bus
     */
    @Resource(name = "cxf")
    public final void setBus(Bus bus) {
        assert this.bus == null || this.bus == bus;
        this.bus = bus;
        if (bus != null) {
            bus.setExtension(this, NettyHttpServerEngineFactory.class);
            lifeCycleManager = bus.getExtension(BusLifeCycleManager.class);
            if (null != lifeCycleManager) {
                lifeCycleManager.registerLifeCycleListener(this);
            }
        }
    }

    public void initComplete() {
        // do nothing here
    }

    public void postShutdown() {
        // shut down the Netty server in the portMap
        // To avoid the CurrentModificationException,
        // do not use portMap.values directly
        NettyHttpServerEngine[] engines = portMap.values().toArray(new NettyHttpServerEngine[portMap.values().size()]);
        for (NettyHttpServerEngine engine : engines) {
            engine.shutdown();
        }
    }

    public void preShutdown() {
        // do nothing here
        // just let server registry to call the server stop first
    }

    private static NettyHttpServerEngine getOrCreate(NettyHttpServerEngineFactory factory,
                                                     String host,
                                                     int port
    ) throws IOException {

        NettyHttpServerEngine ref = portMap.get(port);
        if (ref == null) {
            ref = new NettyHttpServerEngine(host, port);

            NettyHttpServerEngine tmpRef = portMap.putIfAbsent(port, ref);

            if (tmpRef != null) {
                ref = tmpRef;
            }
        }
        return ref;
    }


    public synchronized NettyHttpServerEngine retrieveNettyHttpServerEngine(int port) {
        return portMap.get(port);
    }


    public synchronized NettyHttpServerEngine createNettyHttpServerEngine(String host, int port,
                                                                          String protocol) throws IOException {
        LOG.fine("Creating Jetty HTTP Server Engine for port " + port + ".");
        NettyHttpServerEngine ref = getOrCreate(this, host, port);
        // checking the protocol
        if (!protocol.equals(ref.getProtocol())) {
            throw new IOException("Protocol mismatch for port " + port + ": "
                    + "engine's protocol is " + ref.getProtocol()
                    + ", the url protocol is " + protocol);
        }


        return ref;
    }

    public synchronized NettyHttpServerEngine createNettyHttpServerEngine(int port,
                                                                          String protocol) throws IOException {
        return createNettyHttpServerEngine(null, port, protocol);
    }

    /**
     * This method removes the Server Engine from the port map and stops it.
     */
    public static synchronized void destroyForPort(int port) {
        NettyHttpServerEngine ref = portMap.remove(port);
        if (ref != null) {
            LOG.fine("Stopping Jetty HTTP Server Engine on port " + port + ".");
            try {
                ref.shutdown();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
}
