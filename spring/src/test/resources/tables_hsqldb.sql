/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

--
-- In your Quartz properties file, you'll need to set
-- org.quartz.jobStore.driverDelegateClass = org.quartz.impl.jdbcjobstore.HSQLDBDelegate
--

DROP TABLE qrtz_locks IF EXISTS;
DROP TABLE qrtz_scheduler_state IF EXISTS;
DROP TABLE qrtz_fired_triggers IF EXISTS;
DROP TABLE qrtz_paused_trigger_grps IF EXISTS;
DROP TABLE qrtz_calendars IF EXISTS;
DROP TABLE qrtz_blob_triggers IF EXISTS;
DROP TABLE qrtz_cron_triggers IF EXISTS;
DROP TABLE qrtz_simple_triggers IF EXISTS;
DROP TABLE qrtz_simprop_triggers IF EXISTS;
DROP TABLE qrtz_triggers IF EXISTS;
DROP TABLE qrtz_job_details IF EXISTS;

CREATE TABLE qrtz_job_details
(
SCHED_NAME VARCHAR(120) NOT NULL,
JOB_NAME LONGVARCHAR(200) NOT NULL,
JOB_GROUP LONGVARCHAR(200) NOT NULL,
DESCRIPTION LONGVARCHAR(250) NULL,
JOB_CLASS_NAME LONGVARCHAR(250) NOT NULL,
IS_DURABLE BOOLEAN NOT NULL,
IS_NONCONCURRENT BOOLEAN NOT NULL,
IS_UPDATE_DATA BOOLEAN NOT NULL,
REQUESTS_RECOVERY BOOLEAN NOT NULL,
JOB_DATA VARBINARY(64000) NULL,
PRIMARY KEY (SCHED_NAME,JOB_NAME,JOB_GROUP)
);

CREATE TABLE qrtz_triggers
(
SCHED_NAME VARCHAR(120) NOT NULL,
TRIGGER_NAME LONGVARCHAR(200) NOT NULL,
TRIGGER_GROUP LONGVARCHAR(200) NOT NULL,
JOB_NAME LONGVARCHAR(200) NOT NULL,
JOB_GROUP LONGVARCHAR(200) NOT NULL,
DESCRIPTION LONGVARCHAR(250) NULL,
NEXT_FIRE_TIME NUMERIC(13) NULL,
PREV_FIRE_TIME NUMERIC(13) NULL,
PRIORITY INTEGER NULL,
TRIGGER_STATE LONGVARCHAR(16) NOT NULL,
TRIGGER_TYPE LONGVARCHAR(8) NOT NULL,
START_TIME NUMERIC(13) NOT NULL,
END_TIME NUMERIC(13) NULL,
CALENDAR_NAME LONGVARCHAR(200) NULL,
MISFIRE_INSTR NUMERIC(2) NULL,
JOB_DATA VARBINARY(64000) NULL,
PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
FOREIGN KEY (SCHED_NAME,JOB_NAME,JOB_GROUP)
REFERENCES QRTZ_JOB_DETAILS(SCHED_NAME,JOB_NAME,JOB_GROUP)
);

CREATE TABLE qrtz_simple_triggers
(
SCHED_NAME VARCHAR(120) NOT NULL,
TRIGGER_NAME LONGVARCHAR(200) NOT NULL,
TRIGGER_GROUP LONGVARCHAR(200) NOT NULL,
REPEAT_COUNT NUMERIC(7) NOT NULL,
REPEAT_INTERVAL NUMERIC(12) NOT NULL,
TIMES_TRIGGERED NUMERIC(10) NOT NULL,
PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
FOREIGN KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
REFERENCES QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
);

CREATE TABLE qrtz_cron_triggers
(
SCHED_NAME VARCHAR(120) NOT NULL,
TRIGGER_NAME LONGVARCHAR(200) NOT NULL,
TRIGGER_GROUP LONGVARCHAR(200) NOT NULL,
CRON_EXPRESSION LONGVARCHAR(200) NOT NULL,
TIME_ZONE_ID LONGVARCHAR(80),
PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
FOREIGN KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
REFERENCES QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
);

CREATE TABLE qrtz_simprop_triggers
  (
    SCHED_NAME VARCHAR(120) NOT NULL,
    TRIGGER_NAME LONGVARCHAR(200) NOT NULL,
    TRIGGER_GROUP LONGVARCHAR(200) NOT NULL,
    STR_PROP_1 LONGVARCHAR(512) NULL,
    STR_PROP_2 LONGVARCHAR(512) NULL,
    STR_PROP_3 LONGVARCHAR(512) NULL,
    INT_PROP_1 NUMERIC(13) NULL,
    INT_PROP_2 NUMERIC(13) NULL,
    LONG_PROP_1 NUMERIC(13) NULL,
    LONG_PROP_2 NUMERIC(13) NULL,
    DEC_PROP_1 NUMERIC(13,4) NULL,
    DEC_PROP_2 NUMERIC(13,4) NULL,
    BOOL_PROP_1 BOOLEAN NULL,
    BOOL_PROP_2 BOOLEAN NULL,
    PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
    REFERENCES QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
);

CREATE TABLE qrtz_blob_triggers
(
SCHED_NAME VARCHAR(120) NOT NULL,
TRIGGER_NAME LONGVARCHAR(200) NOT NULL,
TRIGGER_GROUP LONGVARCHAR(200) NOT NULL,
BLOB_DATA VARBINARY(64000) NULL,
PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
FOREIGN KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
REFERENCES QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
);

CREATE TABLE qrtz_calendars
(
SCHED_NAME VARCHAR(120) NOT NULL,
CALENDAR_NAME LONGVARCHAR(200) NOT NULL,
CALENDAR VARBINARY(64000) NOT NULL,
PRIMARY KEY (SCHED_NAME,CALENDAR_NAME)
);

CREATE TABLE qrtz_paused_trigger_grps
  (
    SCHED_NAME VARCHAR(120) NOT NULL,
    TRIGGER_GROUP  LONGVARCHAR(200) NOT NULL,
    PRIMARY KEY (SCHED_NAME,TRIGGER_GROUP)
);

CREATE TABLE qrtz_fired_triggers
  (
    SCHED_NAME VARCHAR(120) NOT NULL,
    ENTRY_ID LONGVARCHAR(95) NOT NULL,
    TRIGGER_NAME LONGVARCHAR(200) NOT NULL,
    TRIGGER_GROUP LONGVARCHAR(200) NOT NULL,
    INSTANCE_NAME LONGVARCHAR(200) NOT NULL,
    FIRED_TIME NUMERIC(13) NOT NULL,
    SCHED_TIME NUMERIC(13) NOT NULL,
    PRIORITY INTEGER NOT NULL,
    STATE LONGVARCHAR(16) NOT NULL,
    JOB_NAME LONGVARCHAR(200) NULL,
    JOB_GROUP LONGVARCHAR(200) NULL,
    IS_NONCONCURRENT BOOLEAN NULL,
    REQUESTS_RECOVERY BOOLEAN NULL,
    PRIMARY KEY (SCHED_NAME,ENTRY_ID)
);

CREATE TABLE qrtz_scheduler_state
  (
    SCHED_NAME VARCHAR(120) NOT NULL,
    INSTANCE_NAME LONGVARCHAR(200) NOT NULL,
    LAST_CHECKIN_TIME NUMERIC(13) NOT NULL,
    CHECKIN_INTERVAL NUMERIC(13) NOT NULL,
    PRIMARY KEY (SCHED_NAME,INSTANCE_NAME)
);

CREATE TABLE qrtz_locks
  (
    SCHED_NAME VARCHAR(120) NOT NULL,
    LOCK_NAME  LONGVARCHAR(40) NOT NULL,
    PRIMARY KEY (SCHED_NAME,LOCK_NAME)
);
