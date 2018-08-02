/**
 * Copyright 2018 AppDynamics, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.appdynamics.extensions.kafka;


import com.appdynamics.extensions.kafka.utils.Constants;
import com.appdynamics.extensions.util.YmlUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.rmi.RMIConnectorServer;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JMXConnectionAdapter {

    private final JMXServiceURL serviceUrl;
    private final String username;
    private final String password;

    private JMXConnectionAdapter(Map<String, String> requestMap) throws MalformedURLException {
        if(!Strings.isNullOrEmpty(requestMap.get(Constants.SERVICE_URL)))
            this.serviceUrl = new JMXServiceURL(requestMap.get(Constants.SERVICE_URL));
        else
            this.serviceUrl = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" +
                    requestMap.get(Constants.HOST) + ":" + requestMap.get(Constants.PORT) + "/jmxrmi");
        this.username = requestMap.get(Constants.USERNAME);
        this.password = requestMap.get(Constants.PASSWORD);
    }

    static JMXConnectionAdapter create(final Map<String, String> requestMap) throws MalformedURLException {
        return new JMXConnectionAdapter(requestMap);
    }

    JMXConnector open(boolean useSsl, String encryptionKey, Map<String, ?> connectionMap) throws IOException {
        JMXConnector jmxConnector;
        final Map<String, Object> env = new HashMap<String, Object>();
        boolean useDefaultSslFactory= YmlUtils.getBoolean(connectionMap.get("useDefaultSslConnectionFactory"));
        //todo: pass configurations\ instead of Yml Utils
        if(useSsl) {
            if (Preconditions.checkNotNull(useDefaultSslFactory) && useDefaultSslFactory){
                SslRMIClientSocketFactory sslRMIClientSocketFactory = new SslRMIClientSocketFactory();
                env.put(RMIConnectorServer.RMI_CLIENT_SOCKET_FACTORY_ATTRIBUTE, sslRMIClientSocketFactory);
            } else if (Preconditions.checkNotNull(useDefaultSslFactory) && !useDefaultSslFactory) {
                CustomSSLSocketFactory customSSLSocketFactory = new CustomSSLSocketFactory();
                env.put(RMIConnectorServer.RMI_CLIENT_SOCKET_FACTORY_ATTRIBUTE
                        //todo: handle null
                        ,customSSLSocketFactory.createSocketFactory( encryptionKey, connectionMap));
            }
        }
        if (!Strings.isNullOrEmpty(this.username)) {
            env.put(JMXConnector.CREDENTIALS, new String[]{username, password});
        }
        jmxConnector = JMXConnectorFactory.connect(this.serviceUrl,env);
        if (jmxConnector == null) { throw new IOException("Unable to connect to Mbean server"); }
        return jmxConnector;
    }


    JMXConnector open(boolean useSsl, Map<String, ?> connectionMap) throws IOException {
        JMXConnector jmxConnector;
                final Map<String, Object> env = new HashMap<String, Object>();
        boolean useDefaultSslFactory= YmlUtils.getBoolean(connectionMap.get("useDefaultSslConnectionFactory"));

        if(useSsl) {
            if (Preconditions.checkNotNull(useDefaultSslFactory) && useDefaultSslFactory){
                SslRMIClientSocketFactory sslRMIClientSocketFactory = new SslRMIClientSocketFactory();
                env.put(RMIConnectorServer.RMI_CLIENT_SOCKET_FACTORY_ATTRIBUTE, sslRMIClientSocketFactory);
            } else if (Preconditions.checkNotNull(useDefaultSslFactory) && !useDefaultSslFactory) {
                CustomSSLSocketFactory customSSLSocketFactory = new CustomSSLSocketFactory();
                env.put(RMIConnectorServer.RMI_CLIENT_SOCKET_FACTORY_ATTRIBUTE
                        ,customSSLSocketFactory.createSocketFactory(connectionMap));
            }
        }//todo: remove redundant calls to same method
        if (!Strings.isNullOrEmpty(this.username)) {
            env.put(JMXConnector.CREDENTIALS, new String[]{username, password});
        }
        jmxConnector = JMXConnectorFactory.connect(this.serviceUrl,env);
        if (jmxConnector == null) { throw new IOException("Unable to connect to Mbean server"); }
        return jmxConnector;
    }

    void close(JMXConnector jmxConnector) throws IOException {
        if (jmxConnector != null) {
            jmxConnector.close();
        }
    }

    public Set<ObjectInstance> queryMBeans(final JMXConnector jmxConnection, final ObjectName objectName)
            throws IOException {
        MBeanServerConnection connection = jmxConnection.getMBeanServerConnection();
        return connection.queryMBeans(objectName, null);
    }

    public List<String> getReadableAttributeNames(final JMXConnector jmxConnection, final ObjectInstance instance)
            throws IntrospectionException, ReflectionException, InstanceNotFoundException, IOException {
        MBeanServerConnection connection = jmxConnection.getMBeanServerConnection();
        List<String> attributeNames = Lists.newArrayList();
        MBeanAttributeInfo[] attributes = connection.getMBeanInfo(instance.getObjectName()).getAttributes();
        for (MBeanAttributeInfo attribute : attributes) {
            if (attribute.isReadable()) {
                attributeNames.add(attribute.getName());
            }
        }
        return attributeNames;
    }

    public List<Attribute> getAttributes(final JMXConnector jmxConnection, final ObjectName objectName,
                                         final String[] strings) throws IOException, ReflectionException,
            InstanceNotFoundException {
        MBeanServerConnection connection = jmxConnection.getMBeanServerConnection();
        AttributeList list = connection.getAttributes(objectName, strings);
        if (list != null) {
            return list.asList();
        }
        return Lists.newArrayList();
    }
}
