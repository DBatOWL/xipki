-- IGNORE-ERROR
ALTER TABLE CA DROP CONSTRAINT FK_CA_CRL_SIGNER1;
-- IGNORE-ERROR
ALTER TABLE CAALIAS DROP CONSTRAINT FK_CAALIAS_CA1;
-- IGNORE-ERROR
ALTER TABLE CA_HAS_REQUESTOR DROP CONSTRAINT FK_CA_HAS_REQUESTOR_REQUESTOR1;
-- IGNORE-ERROR
ALTER TABLE CA_HAS_REQUESTOR DROP CONSTRAINT FK_CA_HAS_REQUESTOR_CA1;
-- IGNORE-ERROR
ALTER TABLE CA_HAS_PUBLISHER DROP CONSTRAINT FK_CA_HAS_PUBLISHER_PUBLISHER1;
-- IGNORE-ERROR
ALTER TABLE CA_HAS_PUBLISHER DROP CONSTRAINT FK_CA_HAS_PUBLISHER_CA1;
-- IGNORE-ERROR
ALTER TABLE CA_HAS_PROFILE DROP CONSTRAINT FK_CA_HAS_PROFILE_PROFILE1;
-- IGNORE-ERROR
ALTER TABLE CA_HAS_PROFILE DROP CONSTRAINT FK_CA_HAS_PROFILE_CA1;

DROP TABLE IF EXISTS DBSCHEMA;
DROP TABLE IF EXISTS SYSTEM_EVENT;
DROP TABLE IF EXISTS KEYPAIR_GEN;
DROP TABLE IF EXISTS SIGNER;
DROP TABLE IF EXISTS REQUESTOR;
DROP TABLE IF EXISTS PUBLISHER;
DROP TABLE IF EXISTS PROFILE;
DROP TABLE IF EXISTS CA;
DROP TABLE IF EXISTS CAALIAS;
DROP TABLE IF EXISTS CA_HAS_REQUESTOR;
DROP TABLE IF EXISTS CA_HAS_PUBLISHER;
DROP TABLE IF EXISTS CA_HAS_PROFILE;

DROP TABLE IF EXISTS CRL;
DROP TABLE IF EXISTS CERT;

-- changeset xipki:1
CREATE TABLE DBSCHEMA (
    NAME VARCHAR2(45) NOT NULL,
    VALUE2 VARCHAR2(100) NOT NULL,
    CONSTRAINT PK_DBSCHEMA PRIMARY KEY (NAME)
);

INSERT INTO DBSCHEMA (NAME, VALUE2) VALUES ('VENDOR', 'XIPKI');
INSERT INTO DBSCHEMA (NAME, VALUE2) VALUES ('VERSION', '8');
INSERT INTO DBSCHEMA (NAME, VALUE2) VALUES ('X500NAME_MAXLEN', '350');

CREATE TABLE SYSTEM_EVENT (
    NAME VARCHAR2(45) NOT NULL,
    EVENT_TIME NUMBER(38, 0) NOT NULL,
    EVENT_TIME2 TIMESTAMP,
    EVENT_OWNER VARCHAR2(255) NOT NULL,
    CONSTRAINT PK_SYSTEM_EVENT PRIMARY KEY (NAME)
);

COMMENT ON COLUMN SYSTEM_EVENT.EVENT_TIME IS 'seconds since January 1, 1970, 00:00:00 GMT';

CREATE TABLE KEYPAIR_GEN (
    NAME VARCHAR2(45) NOT NULL,
    TYPE VARCHAR2(100) NOT NULL,
    CONF CLOB,
    CONSTRAINT PK_KEYPAIR_GEN PRIMARY KEY (NAME)
);

INSERT INTO KEYPAIR_GEN (NAME, TYPE) VALUES ('software', 'SOFTWARE');

CREATE TABLE SIGNER (
    NAME VARCHAR2(45) NOT NULL,
    TYPE VARCHAR2(100) NOT NULL,
    CERT VARCHAR2(6000),
    CONF CLOB,
    CONSTRAINT PK_SIGNER PRIMARY KEY (NAME)
);

CREATE TABLE REQUESTOR (
    ID NUMBER(5) NOT NULL,
    NAME VARCHAR2(45) NOT NULL,
    TYPE VARCHAR2(100) NOT NULL,
    CONF CLOB,
    CONSTRAINT PK_REQUESTOR PRIMARY KEY (ID)
);

ALTER TABLE REQUESTOR ADD CONSTRAINT CONST_REQUESTOR_NAME UNIQUE (NAME);

CREATE TABLE PUBLISHER (
    ID NUMBER(5) NOT NULL,
    NAME VARCHAR2(45) NOT NULL,
    TYPE VARCHAR2(100) NOT NULL,
    CONF CLOB,
    CONSTRAINT PK_PUBLISHER PRIMARY KEY (ID)
);

COMMENT ON COLUMN PUBLISHER.NAME IS 'duplication is not permitted';

ALTER TABLE PUBLISHER ADD CONSTRAINT CONST_PUBLISHER_NAME UNIQUE (NAME);

CREATE TABLE PROFILE (
    ID NUMBER(5) NOT NULL,
    NAME VARCHAR2(45) NOT NULL,
    TYPE VARCHAR2(100) NOT NULL,
    CONF CLOB,
    CONSTRAINT PK_PROFILE PRIMARY KEY (ID)
);

COMMENT ON COLUMN PROFILE.NAME IS 'duplication is not permitted';
COMMENT ON COLUMN PROFILE.CONF IS 'profile data, depends on the type';

ALTER TABLE PROFILE ADD CONSTRAINT CONST_PROFILE_NAME UNIQUE (NAME);

CREATE TABLE CA (
    ID NUMBER(5) NOT NULL,
    NAME VARCHAR2(45) NOT NULL,
    STATUS VARCHAR2(10) NOT NULL,
    NEXT_CRLNO NUMBER(38, 0),
    CRL_SIGNER_NAME VARCHAR2(45),
    SUBJECT VARCHAR2(350) NOT NULL,
    REV_INFO VARCHAR2(200),
    CERT VARCHAR2(6000) NOT NULL,
    SIGNER_TYPE VARCHAR2(100) NOT NULL,
    SIGNER_CONF CLOB NOT NULL,
    CERTCHAIN CLOB,
    CONF CLOB NOT NULL,
    CONSTRAINT PK_CA PRIMARY KEY (ID)
);

COMMENT ON COLUMN CA.NAME IS 'duplication is not permitted';
COMMENT ON COLUMN CA.STATUS IS 'valid values: active, inactive';
COMMENT ON COLUMN CA.REV_INFO IS 'CA revocation information';
COMMENT ON COLUMN CA.CERTCHAIN IS 'Certificate chain without CA''s certificate';

ALTER TABLE CA ADD CONSTRAINT CONST_CA_NAME UNIQUE (NAME);

CREATE TABLE CAALIAS (
    NAME VARCHAR2(45) NOT NULL,
    CA_ID NUMBER(5) NOT NULL,
    CONSTRAINT PK_CAALIAS PRIMARY KEY (NAME)
);

CREATE TABLE CA_HAS_REQUESTOR (
    CA_ID NUMBER(5) NOT NULL,
    REQUESTOR_ID NUMBER(5) NOT NULL,
    PERMISSION INTEGER,
    PROFILES VARCHAR2(500),
    CONSTRAINT PK_CA_HAS_REQUESTOR PRIMARY KEY (CA_ID, REQUESTOR_ID)
);

CREATE TABLE CA_HAS_PUBLISHER (
    CA_ID NUMBER(5) NOT NULL,
    PUBLISHER_ID NUMBER(5) NOT NULL,
    CONSTRAINT PK_CA_HAS_PUBLISHER PRIMARY KEY (CA_ID, PUBLISHER_ID)
);

CREATE TABLE CA_HAS_PROFILE (
    CA_ID NUMBER(5) NOT NULL,
    PROFILE_ID NUMBER(5) NOT NULL,
    CONSTRAINT PK_CA_HAS_PROFILE PRIMARY KEY (CA_ID, PROFILE_ID)
);

-- changeset xipki:3
ALTER TABLE CA ADD CONSTRAINT FK_CA_CRL_SIGNER1
    FOREIGN KEY (CRL_SIGNER_NAME) REFERENCES SIGNER (NAME);

ALTER TABLE CAALIAS ADD CONSTRAINT FK_CAALIAS_CA1
    FOREIGN KEY (CA_ID) REFERENCES CA (ID)
    ON DELETE CASCADE;

ALTER TABLE CA_HAS_REQUESTOR ADD CONSTRAINT FK_CA_HAS_REQUESTOR_REQUESTOR1
    FOREIGN KEY (REQUESTOR_ID) REFERENCES REQUESTOR (ID)
    ON DELETE CASCADE;

ALTER TABLE CA_HAS_REQUESTOR ADD CONSTRAINT FK_CA_HAS_REQUESTOR_CA1
    FOREIGN KEY (CA_ID) REFERENCES CA (ID)
    ON DELETE CASCADE;

ALTER TABLE CA_HAS_PUBLISHER ADD CONSTRAINT FK_CA_HAS_PUBLISHER_PUBLISHER1
    FOREIGN KEY (PUBLISHER_ID) REFERENCES PUBLISHER (ID)
    ON DELETE CASCADE;

ALTER TABLE CA_HAS_PUBLISHER ADD CONSTRAINT FK_CA_HAS_PUBLISHER_CA1
    FOREIGN KEY (CA_ID) REFERENCES CA (ID)
    ON DELETE CASCADE;

ALTER TABLE CA_HAS_PROFILE ADD CONSTRAINT FK_CA_HAS_PROFILE_PROFILE1
    FOREIGN KEY (PROFILE_ID) REFERENCES PROFILE (ID)
    ON DELETE CASCADE;

ALTER TABLE CA_HAS_PROFILE ADD CONSTRAINT FK_CA_HAS_PROFILE_CA1
    FOREIGN KEY (CA_ID) REFERENCES CA (ID)
    ON DELETE CASCADE;
