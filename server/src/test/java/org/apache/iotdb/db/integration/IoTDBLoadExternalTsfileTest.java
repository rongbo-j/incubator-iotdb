/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.db.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.apache.iotdb.db.engine.StorageEngine;
import org.apache.iotdb.db.engine.storagegroup.TsFileResource;
import org.apache.iotdb.db.exception.StorageEngineException;
import org.apache.iotdb.db.service.IoTDB;
import org.apache.iotdb.db.utils.EnvironmentUtils;
import org.apache.iotdb.jdbc.Config;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IoTDBLoadExternalTsfileTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(IoTDBLoadExternalTsfileTest.class);
  private static IoTDB daemon;
  private static String[] insertSequenceSqls = new String[]{
      "SET STORAGE GROUP TO root.vehicle",
      "SET STORAGE GROUP TO root.test",
      "CREATE TIMESERIES root.vehicle.d0.s0 WITH DATATYPE=INT32, ENCODING=RLE",
      "CREATE TIMESERIES root.vehicle.d0.s1 WITH DATATYPE=TEXT, ENCODING=PLAIN",
      "CREATE TIMESERIES root.vehicle.d1.s2 WITH DATATYPE=FLOAT, ENCODING=RLE",
      "CREATE TIMESERIES root.vehicle.d1.s3 WITH DATATYPE=BOOLEAN, ENCODING=PLAIN",
      "CREATE TIMESERIES root.test.d0.s0 WITH DATATYPE=INT32, ENCODING=RLE",
      "CREATE TIMESERIES root.test.d0.s1 WITH DATATYPE=TEXT, ENCODING=PLAIN",
      "CREATE TIMESERIES root.test.d1.g0.s0 WITH DATATYPE=INT32, ENCODING=RLE",
      "insert into root.vehicle.d0(timestamp,s0) values(10,100)",
      "insert into root.vehicle.d0(timestamp,s0,s1) values(12,101,'102')",
      "insert into root.vehicle.d0(timestamp,s1) values(19,'103')",
      "insert into root.vehicle.d1(timestamp,s2) values(11,104.0)",
      "insert into root.vehicle.d1(timestamp,s2,s3) values(15,105.0,true)",
      "insert into root.vehicle.d1(timestamp,s3) values(17,false)",
      "insert into root.vehicle.d0(timestamp,s0) values(20,1000)",
      "insert into root.test.d0(timestamp,s0) values(10,106)",
      "insert into root.test.d0(timestamp,s0,s1) values(14,107,'108')",
      "insert into root.test.d0(timestamp,s1) values(16,'109')",
      "insert into root.test.d1.g0(timestamp,s0) values(1,110)",
      "insert into root.test.d0(timestamp,s0) values(30,1006)",
      "insert into root.test.d0(timestamp,s0,s1) values(34,1007,'1008')",
      "insert into root.test.d0(timestamp,s1) values(36,'1090')",
      "insert into root.test.d1.g0(timestamp,s0) values(10,1100)",
      "flush",
      "insert into root.test.d0(timestamp,s0) values(150,126)",
      "insert into root.test.d0(timestamp,s0,s1) values(80,127,'128')",
      "insert into root.test.d0(timestamp,s1) values(200,'129')",
      "insert into root.test.d1.g0(timestamp,s0) values(140,430)",
      "insert into root.test.d0(timestamp,s0) values(150,426)",
      "flush"
  };

  private static String[] insertUnsequenceSqls = new String[]{
      "insert into root.vehicle.d0(timestamp,s0) values(6,120)",
      "insert into root.vehicle.d0(timestamp,s0,s1) values(38,121,'122')",
      "insert into root.vehicle.d0(timestamp,s1) values(9,'123')",
      "insert into root.vehicle.d0(timestamp,s0) values(16,128)",
      "insert into root.vehicle.d0(timestamp,s0,s1) values(18,189,'198')",
      "insert into root.vehicle.d0(timestamp,s1) values(99,'1234')",
      "insert into root.vehicle.d1(timestamp,s2) values(14,1024.0)",
      "insert into root.vehicle.d1(timestamp,s2,s3) values(29,1205.0,true)",
      "insert into root.vehicle.d1(timestamp,s3) values(33,true)",
      "insert into root.test.d0(timestamp,s0) values(45,126)",
      "insert into root.test.d0(timestamp,s0,s1) values(68,127,'128')",
      "insert into root.test.d0(timestamp,s1) values(78,'129')",
      "insert into root.test.d1.g0(timestamp,s0) values(14,430)",
      "flush",
      "insert into root.test.d0(timestamp,s0) values(20,426)",
      "insert into root.test.d0(timestamp,s0,s1) values(13,427,'528')",
      "insert into root.test.d0(timestamp,s1) values(2,'1209')",
      "insert into root.test.d1.g0(timestamp,s0) values(4,330)",
      "flush",
  };

  private static final String TIMESTAMP_STR = "Time";
  private static final String VEHICLE_D0_S0_STR = "root.vehicle.d0.s0";
  private static final String VEHICLE_D0_S1_STR = "root.vehicle.d0.s1";
  private static final String VEHICLE_D0_S2_STR = "root.vehicle.d1.s2";
  private static final String VEHICLE_D0_S3_STR = "root.vehicle.d1.s3";
  private static final String TEST_D0_S0_STR = "root.test.d0.s0";
  private static final String TEST_D0_S1_STR = "root.test.d0.s1";
  private static final String TEST_D1_STR = "root.test.d1.g0.s0";

  private static String[] deleteSqls = new String[]{
      "DELETE STORAGE GROUP root.vehicle",
      "DELETE STORAGE GROUP root.test"
  };

  @Before
  public void setUp() throws Exception {
    EnvironmentUtils.closeStatMonitor();
    daemon = IoTDB.getInstance();
    daemon.active();
    EnvironmentUtils.envSetUp();
    Class.forName(Config.JDBC_DRIVER_NAME);
    prepareData(insertSequenceSqls);
  }

  @After
  public void tearDown() throws Exception {
    daemon.stop();
    EnvironmentUtils.cleanEnv();
  }

  @Test
  public void moveTsfileTest() throws SQLException {
    try (Connection connection = DriverManager.
        getConnection("jdbc:iotdb://127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {

      // move root.vehicle
      List<TsFileResource> resources = new ArrayList<>(
          StorageEngine.getInstance().getProcessor("root.vehicle")
              .getSequenceFileList());
      assertEquals(1, resources.size());
      File tmpDir = new File(resources.get(0).getFile().getParentFile().getParentFile(),
          "tmp" + File.separator + "root.vehicle");
      if (!tmpDir.exists()) {
        tmpDir.mkdirs();
      }
      for (TsFileResource resource : resources) {
        statement.execute(String.format("move %s %s", resource.getFile().getPath(), tmpDir));
      }
      assertEquals(0, StorageEngine.getInstance().getProcessor("root.vehicle")
          .getSequenceFileList().size());
      assertNotNull(tmpDir.listFiles());
      assertEquals(1, tmpDir.listFiles().length >> 1);

      // move root.test
      resources = new ArrayList<>(
          StorageEngine.getInstance().getProcessor("root.test")
              .getSequenceFileList());
      assertEquals(2, resources.size());
      tmpDir = new File(resources.get(0).getFile().getParentFile().getParentFile(),
          "tmp" + File.separator + "root.test");
      if (!tmpDir.exists()) {
        tmpDir.mkdirs();
      }
      for (TsFileResource resource : resources) {
        statement.execute(String.format("move %s %s", resource.getFile().getPath(), tmpDir));
      }
      assertEquals(0, StorageEngine.getInstance().getProcessor("root.test")
          .getSequenceFileList().size());
      assertNotNull(tmpDir.listFiles());
      assertEquals(2, tmpDir.listFiles().length >> 1);
    } catch (StorageEngineException e) {
      Assert.fail();
    }
  }

  @Test
  public void loadSequenceTsfileTest() throws SQLException {
    try (Connection connection = DriverManager.
        getConnection("jdbc:iotdb://127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {

      // move root.vehicle
      List<TsFileResource> resources = new ArrayList<>(
          StorageEngine.getInstance().getProcessor("root.vehicle")
              .getSequenceFileList());
      File tmpDir = new File(resources.get(0).getFile().getParentFile().getParentFile(),
          "tmp" + File.separator + "root.vehicle");
      if (!tmpDir.exists()) {
        tmpDir.mkdirs();
      }
      for (TsFileResource resource : resources) {
        statement.execute(String.format("move %s %s", resource.getFile().getPath(), tmpDir));
      }

      // move root.test
      resources = new ArrayList<>(
          StorageEngine.getInstance().getProcessor("root.test")
              .getSequenceFileList());
      tmpDir = new File(resources.get(0).getFile().getParentFile().getParentFile(),
          "tmp" + File.separator + "root.test");
      if (!tmpDir.exists()) {
        tmpDir.mkdirs();
      }
      for (TsFileResource resource : resources) {
        statement.execute(String.format("move %s %s", resource.getFile().getPath(), tmpDir));
      }

      // load all tsfile in tmp dir
      tmpDir = new File(resources.get(0).getFile().getParentFile().getParentFile(), "tmp");
      statement.execute(String.format("load %s", tmpDir.getAbsolutePath()));
      resources = new ArrayList<>(
          StorageEngine.getInstance().getProcessor("root.vehicle")
              .getSequenceFileList());
      assertEquals(1, resources.size());
      resources = new ArrayList<>(
          StorageEngine.getInstance().getProcessor("root.test")
              .getSequenceFileList());
      assertEquals(2, resources.size());
      assertNotNull(tmpDir.listFiles());
      assertEquals(0, new File(tmpDir, "root.vehicle").listFiles().length);
      assertEquals(0, new File(tmpDir, "root.test").listFiles().length);
    } catch (StorageEngineException e) {
      Assert.fail();
    }
  }

  @Test
  public void loadUnsequenceTsfileTest() throws SQLException {
    prepareData(insertUnsequenceSqls);
    String[] queryRes = new String[]{
        "1,null,null,null,null,null,null,110",
        "2,null,null,null,null,null,1209,null",
        "4,null,null,null,null,null,null,330",
        "6,120,null,null,null,null,null,null",
        "9,null,123,null,null,null,null,null",
        "10,100,null,null,null,106,null,1100",
        "11,null,null,104.0,null,null,null,null",
        "12,101,102,null,null,null,null,null",
        "13,null,null,null,null,427,528,null",
        "14,null,null,1024.0,null,107,108,430",
        "15,null,null,105.0,true,null,null,null",
        "16,128,null,null,null,null,109,null",
        "17,null,null,null,false,null,null,null",
        "18,189,198,null,null,null,null,null",
        "19,null,103,null,null,null,null,null",
        "20,1000,null,null,null,426,null,null",
        "29,null,null,1205.0,true,null,null,null",
        "30,null,null,null,null,1006,null,null",
        "33,null,null,null,true,null,null,null",
        "34,null,null,null,null,1007,1008,null",
        "36,null,null,null,null,null,1090,null",
        "38,121,122,null,null,null,null,null",
        "45,null,null,null,null,126,null,null",
        "68,null,null,null,null,127,128,null",
        "78,null,null,null,null,null,129,null",
        "80,null,null,null,null,127,128,null",
        "99,null,1234,null,null,null,null,null",
        "140,null,null,null,null,null,null,430",
        "150,null,null,null,null,426,null,null",
        "200,null,null,null,null,null,129,null"
    };
    try (Connection connection = DriverManager.
        getConnection("jdbc:iotdb://127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {

      // check query result
      boolean hasResultSet = statement.execute("select  * from root");
      Assert.assertTrue(hasResultSet);
      try (ResultSet resultSet = statement.getResultSet()) {
        int cnt = 0;
        while (resultSet.next()) {
          String queryString = resultSet.getString(TIMESTAMP_STR) + "," + resultSet.getString(
              VEHICLE_D0_S0_STR) + "," + resultSet.getString(VEHICLE_D0_S1_STR)
              + "," + resultSet.getString(VEHICLE_D0_S2_STR) + "," + resultSet
              .getString(VEHICLE_D0_S3_STR)
              + "," + resultSet.getString(TEST_D0_S0_STR) + "," + resultSet
              .getString(TEST_D0_S1_STR)
              + "," + resultSet.getString(TEST_D1_STR);
          Assert.assertEquals(queryRes[cnt++], queryString);
        }
      }

      // move root.vehicle
      List<TsFileResource> resources = new ArrayList<>(
          StorageEngine.getInstance().getProcessor("root.vehicle").getSequenceFileList());
      assertEquals(2, resources.size());
      File tmpDir = new File(
          resources.get(0).getFile().getParentFile().getParentFile().getParentFile(),
          "tmp" + File.separator + "root.vehicle");
      if (!tmpDir.exists()) {
        tmpDir.mkdirs();
      }
      for (TsFileResource resource : resources) {
        statement.execute(String.format("move %s %s", resource.getFile().getPath(), tmpDir));
      }
      resources = new ArrayList<>(
          StorageEngine.getInstance().getProcessor("root.vehicle").getUnSequenceFileList());
      assertEquals(1, resources.size());
      for (TsFileResource resource : resources) {
        statement.execute(String.format("move %s %s", resource.getFile().getPath(), tmpDir));
      }

      // move root.test
      resources = new ArrayList<>(
          StorageEngine.getInstance().getProcessor("root.test").getSequenceFileList());
      assertEquals(2, resources.size());
      tmpDir = new File(tmpDir.getParent(), "root.test");
      if (!tmpDir.exists()) {
        tmpDir.mkdirs();
      }
      for (TsFileResource resource : resources) {
        statement.execute(String.format("move %s %s", resource.getFile().getPath(), tmpDir));
      }
      resources = new ArrayList<>(
          StorageEngine.getInstance().getProcessor("root.test").getUnSequenceFileList());
      assertEquals(2, resources.size());
      for (TsFileResource resource : resources) {
        statement.execute(String.format("move %s %s", resource.getFile().getPath(), tmpDir));
      }

      // load all tsfile in tmp dir
      tmpDir = tmpDir.getParentFile();
      statement.execute(String.format("load %s", tmpDir.getAbsolutePath()));
      assertEquals(2, StorageEngine.getInstance().getProcessor("root.vehicle")
          .getSequenceFileList().size());
      assertEquals(1, StorageEngine.getInstance().getProcessor("root.vehicle")
          .getUnSequenceFileList().size());
      assertEquals(1, StorageEngine.getInstance().getProcessor("root.test")
          .getUnSequenceFileList().size());
      assertEquals(3, StorageEngine.getInstance().getProcessor("root.test")
          .getSequenceFileList().size());
      assertNotNull(tmpDir.listFiles());
      assertEquals(0, new File(tmpDir, "root.vehicle").listFiles().length);
      assertEquals(0, new File(tmpDir, "root.test").listFiles().length);

      // check query result
      hasResultSet = statement.execute("select  * from root");
      Assert.assertTrue(hasResultSet);
      try (ResultSet resultSet = statement.getResultSet()) {
        int cnt = 0;
        while (resultSet.next()) {
          String queryString = resultSet.getString(TIMESTAMP_STR) + "," + resultSet.getString(
              VEHICLE_D0_S0_STR) + "," + resultSet.getString(VEHICLE_D0_S1_STR)
              + "," + resultSet.getString(VEHICLE_D0_S2_STR) + "," + resultSet
              .getString(VEHICLE_D0_S3_STR)
              + "," + resultSet.getString(TEST_D0_S0_STR) + "," + resultSet
              .getString(TEST_D0_S1_STR)
              + "," + resultSet.getString(TEST_D1_STR);
          Assert.assertEquals(queryRes[cnt++], queryString);
        }
      }
    } catch (StorageEngineException e) {
      Assert.fail();
    }
  }

  @Test
  public void loadTsfileTestWithAutoCreateSchema() throws SQLException {
    try (Connection connection = DriverManager.
        getConnection("jdbc:iotdb://127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {

      // move root.vehicle
      List<TsFileResource> resources = new ArrayList<>(
          StorageEngine.getInstance().getProcessor("root.vehicle")
              .getSequenceFileList());
      File tmpDir = new File(resources.get(0).getFile().getParentFile().getParentFile(),
          "tmp" + File.separator + "root.vehicle");
      if (!tmpDir.exists()) {
        tmpDir.mkdirs();
      }
      for (TsFileResource resource : resources) {
        statement.execute(String.format("move %s %s", resource.getFile().getPath(), tmpDir));
      }

      // move root.test
      resources = new ArrayList<>(
          StorageEngine.getInstance().getProcessor("root.test")
              .getSequenceFileList());
      tmpDir = new File(resources.get(0).getFile().getParentFile().getParentFile(),
          "tmp" + File.separator + "root.test");
      if (!tmpDir.exists()) {
        tmpDir.mkdirs();
      }
      for (TsFileResource resource : resources) {
        statement.execute(String.format("move %s %s", resource.getFile().getPath(), tmpDir));
      }

      boolean hasResultSet = statement.execute("show timeseries");
      Assert.assertTrue(hasResultSet);
      StringBuilder timeseriesPath = new StringBuilder();
      try (ResultSet resultSet = statement.getResultSet()) {
        while (resultSet.next()) {
          timeseriesPath.append(
              resultSet.getString(1) + "," + resultSet.getString(2) + "," + resultSet.getString(3));
          timeseriesPath.append(' ');
        }
      }
      Assert.assertEquals(
          "root.vehicle.d0.s0,root.vehicle,INT32 root.vehicle.d0.s1,root.vehicle,TEXT root.vehicle.d1.s2,root.vehicle,FLOAT root.vehicle.d1.s3,root.vehicle,BOOLEAN root.test.d0.s0,root.test,INT32 root.test.d0.s1,root.test,TEXT root.test.d1.g0.s0,root.test,INT32 ",
          timeseriesPath.toString());

      // remove metadata
      for (String sql : deleteSqls) {
        statement.execute(sql);
      }

      // test wrong statement
      boolean hasError = false;
      try {
        statement.execute(String.format("load %s true1 2", tmpDir.getAbsolutePath()));
      } catch (Exception e) {
        hasError = true;
        Assert.assertEquals(
            "Statement format is not right: Please check the statement: load [FILE] true/false [storage group level]",
            e.getMessage());
      }
      Assert.assertTrue(hasError);

      // test not load metadata automatically, it will occur errors.
      hasError = false;
      try {
        statement.execute(String.format("load %s false 2", tmpDir.getAbsolutePath()));
      } catch (Exception e) {
        hasError = true;
      }
      Assert.assertTrue(hasError);

      // test load metadata automatically, it will succeed.
      tmpDir = tmpDir.getParentFile();
      statement.execute(String.format("load %s true 2", tmpDir.getAbsolutePath()));
      resources = new ArrayList<>(
          StorageEngine.getInstance().getProcessor("root.vehicle")
              .getSequenceFileList());
      assertEquals(1, resources.size());
      resources = new ArrayList<>(
          StorageEngine.getInstance().getProcessor("root.test")
              .getSequenceFileList());
      assertEquals(2, resources.size());
      assertEquals(2, tmpDir.listFiles().length);
      for(File dir: tmpDir.listFiles()){
        assertEquals(0, dir.listFiles().length);
      }
    } catch (StorageEngineException e) {
      Assert.fail();
    }
  }

  @Test
  public void removeTsfileTest() throws SQLException {
    try (Connection connection = DriverManager.
        getConnection("jdbc:iotdb://127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {
      List<TsFileResource> resources = new ArrayList<>(
          StorageEngine.getInstance().getProcessor("root.vehicle")
              .getSequenceFileList());
      assertEquals(1, resources.size());
      for (TsFileResource resource : resources) {
        statement.execute(String.format("remove %s", resource.getFile().getPath()));
      }
      assertEquals(0, StorageEngine.getInstance().getProcessor("root.vehicle")
          .getSequenceFileList().size());

      resources = new ArrayList<>(
          StorageEngine.getInstance().getProcessor("root.test")
              .getSequenceFileList());
      assertEquals(2, resources.size());
      for (TsFileResource resource : resources) {
        statement.execute(String.format("remove %s", resource.getFile().getPath()));
      }
      assertEquals(0, StorageEngine.getInstance().getProcessor("root.test")
          .getSequenceFileList().size());
    } catch (StorageEngineException e) {
      Assert.fail();
    }
  }

  private void prepareData(String[] sqls) throws SQLException {
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root",
            "root");
        Statement statement = connection.createStatement()) {

      for (String sql : sqls) {
        statement.execute(sql);
      }

    } catch (Exception e) {
      LOGGER.error("Can not execute sql.", e);
      fail();
    }
  }
}

