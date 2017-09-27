/*
 * Copyright 2017 Confluent Inc.
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

package io.confluent.kafka.schemaregistry.rest;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import io.confluent.kafka.schemaregistry.exceptions.SchemaRegistryException;

public class SslFactory {

  private String protocol;
  private String provider;
  private String kmfAlgorithm;
  private String tmfAlgorithm;
  private SecurityStore keystore = null;
  private String keyPassword;
  private SecurityStore truststore;
  private SSLContext sslContext;


  public void configure(SchemaRegistryConfig configs) throws SchemaRegistryException {
    this.protocol = configs.getString(SchemaRegistryConfig.SSL_PROTOCOL_CONFIG);
    this.provider = configs.getString(SchemaRegistryConfig.SSL_PROVIDER_CONFIG);

    this.kmfAlgorithm = configs.getString(SchemaRegistryConfig.SSL_KEYMANAGER_ALGORITHM_CONFIG);
    this.tmfAlgorithm = configs.getString(SchemaRegistryConfig.SSL_TRUSTMANAGER_ALGORITHM_CONFIG);

    createKeystore(
        configs.getString(SchemaRegistryConfig.SSL_KEYSTORE_TYPE_CONFIG),
        configs.getString(SchemaRegistryConfig.SSL_KEYSTORE_LOCATION_CONFIG),
        configs.getString(SchemaRegistryConfig.SSL_KEYSTORE_PASSWORD_CONFIG),
        configs.getString(SchemaRegistryConfig.SSL_KEY_PASSWORD_CONFIG)
    );

    createTruststore(
        configs.getString(SchemaRegistryConfig.SSL_TRUSTSTORE_TYPE_CONFIG),
        configs.getString(SchemaRegistryConfig.SSL_TRUSTSTORE_LOCATION_CONFIG),
        configs.getString(SchemaRegistryConfig.SSL_TRUSTSTORE_PASSWORD_CONFIG)
    );
    try {
      this.sslContext = createSslContext();
    } catch (Exception e) {
      throw new SchemaRegistryException(e);
    }
  }


  private SSLContext createSslContext() throws GeneralSecurityException, IOException {
    SSLContext sslContext;
    if (provider != null) {
      sslContext = SSLContext.getInstance(protocol, provider);
    } else {
      sslContext = SSLContext.getInstance(protocol);
    }

    KeyManager[] keyManagers = null;
    if (keystore != null) {
      String kmfAlgorithm =
          this.kmfAlgorithm != null ? this.kmfAlgorithm : KeyManagerFactory.getDefaultAlgorithm();
      KeyManagerFactory kmf = KeyManagerFactory.getInstance(kmfAlgorithm);
      KeyStore ks = keystore.load();
      String keyPassword = this.keyPassword != null ? this.keyPassword : keystore.password;
      kmf.init(ks, keyPassword.toCharArray());
      keyManagers = kmf.getKeyManagers();
    }

    String tmfAlgorithm =
        this.tmfAlgorithm != null ? this.tmfAlgorithm : TrustManagerFactory.getDefaultAlgorithm();
    TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
    KeyStore ts = truststore == null ? null : truststore.load();
    tmf.init(ts);

    sslContext.init(keyManagers, tmf.getTrustManagers(), new SecureRandom());
    return sslContext;
  }


  /**
   * Returns a configured SSLContext.
   *
   * @return SSLContext.
   */
  public SSLContext sslContext() {
    return sslContext;
  }

  private void createKeystore(String type, String path, String password, String keyPassword)
      throws SchemaRegistryException {
    if (path == null && password != null) {
      throw new SchemaRegistryException(
          "SSL key store is not specified, but key store password is specified.");
    } else if (path != null && password == null) {
      throw new SchemaRegistryException(
          "SSL key store is specified, but key store password is not specified.");
    } else if (path != null && password != null) {
      this.keystore = new SecurityStore(type, path, password);
      this.keyPassword = keyPassword;
    }
  }

  private void createTruststore(String type, String path, String password)
      throws SchemaRegistryException {
    if (path == null && password != null) {
      throw new SchemaRegistryException(
          "SSL trust store is not specified, but trust store password is specified.");
    } else if (path != null) {
      this.truststore = new SecurityStore(type, path, password);
    }
  }

  private static class SecurityStore {

    private final String type;
    private final String path;
    private final String password;

    private SecurityStore(String type, String path, String password) {
      this.type = type == null ? KeyStore.getDefaultType() : type;
      this.path = path;
      this.password = password;
    }

    private KeyStore load() throws GeneralSecurityException, IOException {
      FileInputStream in = null;
      try {
        KeyStore ks = KeyStore.getInstance(type);
        in = new FileInputStream(path);
        char[] passwordChars = password != null ? password.toCharArray() : null;
        ks.load(in, passwordChars);
        return ks;
      } finally {
        if (in != null) {
          in.close();
        }
      }
    }
  }

}
