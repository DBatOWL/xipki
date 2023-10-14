DROP TABLE IF EXISTS DBSCHEMA CASCADE;
DROP TABLE IF EXISTS SYSTEM_EVENT CASCADE;
DROP TABLE IF EXISTS KEYPAIR_GEN CASCADE;
DROP TABLE IF EXISTS SIGNER CASCADE;
DROP TABLE IF EXISTS REQUESTOR CASCADE;
DROP TABLE IF EXISTS PUBLISHER CASCADE;
DROP TABLE IF EXISTS PROFILE CASCADE;
DROP TABLE IF EXISTS CA CASCADE;
DROP TABLE IF EXISTS CAALIAS CASCADE;
DROP TABLE IF EXISTS CA_HAS_REQUESTOR CASCADE;
DROP TABLE IF EXISTS CA_HAS_PUBLISHER CASCADE;
DROP TABLE IF EXISTS CA_HAS_PROFILE CASCADE;

-- changeset xipki:1
CREATE TABLE DBSCHEMA (
    NAME VARCHAR(45) NOT NULL,
    VALUE2 VARCHAR(100) NOT NULL,
    CONSTRAINT "DBSCHEMA_pkey" PRIMARY KEY (NAME)
);

INSERT INTO DBSCHEMA (NAME, VALUE2) VALUES ('VENDOR', 'XIPKI');
INSERT INTO DBSCHEMA (NAME, VALUE2) VALUES ('VERSION', '9');
INSERT INTO DBSCHEMA (NAME, VALUE2) VALUES ('X500NAME_MAXLEN', '350');

CREATE TABLE SYSTEM_EVENT (
    NAME VARCHAR(45) NOT NULL,
    EVENT_TIME BIGINT NOT NULL,
    EVENT_TIME2 TIMESTAMP WITHOUT TIME ZONE,
    EVENT_OWNER VARCHAR(255) NOT NULL,
    CONSTRAINT "SYSTEM_EVENT_pkey" PRIMARY KEY (NAME)
);

COMMENT ON COLUMN SYSTEM_EVENT.EVENT_TIME IS 'seconds since January 1, 1970, 00:00:00 GMT';

CREATE TABLE KEYPAIR_GEN (
    NAME VARCHAR(45) NOT NULL,
    TYPE VARCHAR(100) NOT NULL,
    CONF TEXT,
    CONSTRAINT "KEYPAIR_GEN_pkey" PRIMARY KEY (NAME)
);

INSERT INTO KEYPAIR_GEN (NAME, TYPE) VALUES ('software', 'SOFTWARE');

CREATE TABLE SIGNER (
    NAME VARCHAR(45) NOT NULL,
    TYPE VARCHAR(100) NOT NULL,
    CERT VARCHAR(6000),
    CONF TEXT,
    CONSTRAINT "SIGNER_pkey" PRIMARY KEY (NAME)
);

CREATE TABLE REQUESTOR (
    ID SMALLINT NOT NULL,
    NAME VARCHAR(45) NOT NULL,
    TYPE VARCHAR(100) NOT NULL,
    CONF TEXT,
    CONSTRAINT "REQUESTOR_pkey" PRIMARY KEY (ID)
);

ALTER TABLE REQUESTOR ADD CONSTRAINT CONST_REQUESTOR_NAME UNIQUE (NAME);

CREATE TABLE PUBLISHER (
    ID SMALLINT NOT NULL,
    NAME VARCHAR(45) NOT NULL,
    TYPE VARCHAR(100) NOT NULL,
    CONF TEXT,
    CONSTRAINT "PUBLISHER_pkey" PRIMARY KEY (ID)
);

COMMENT ON COLUMN PUBLISHER.NAME IS 'duplication is not permitted';

ALTER TABLE PUBLISHER ADD CONSTRAINT CONST_PUBLISHER_NAME UNIQUE (NAME);

CREATE TABLE PROFILE (
    ID SMALLINT NOT NULL,
    NAME VARCHAR(45) NOT NULL,
    TYPE VARCHAR(100) NOT NULL,
    CONF TEXT,
    CONSTRAINT "PROFILE_pkey" PRIMARY KEY (ID)
);

COMMENT ON COLUMN PROFILE.NAME IS 'duplication is not permitted';
COMMENT ON COLUMN PROFILE.CONF IS 'profile data, depends on the type';

ALTER TABLE PROFILE ADD CONSTRAINT CONST_PROFILE_NAME UNIQUE (NAME);

CREATE TABLE CA (
    ID SMALLINT NOT NULL,
    NAME VARCHAR(45) NOT NULL,
    STATUS VARCHAR(10) NOT NULL,
    NEXT_CRLNO BIGINT,
    CRL_SIGNER_NAME VARCHAR(45),
    SUBJECT VARCHAR(350) NOT NULL,
    REV_INFO VARCHAR(200),
    CERT VARCHAR(6000) NOT NULL,
    SIGNER_TYPE VARCHAR(100) NOT NULL,
    SIGNER_CONF TEXT NOT NULL,
    CERTCHAIN TEXT,
    CONF TEXT NOT NULL,
    CONSTRAINT "CA_pkey" PRIMARY KEY (ID)
);

COMMENT ON COLUMN CA.NAME IS 'duplication is not permitted';
COMMENT ON COLUMN CA.STATUS IS 'valid values: active, inactive';
COMMENT ON COLUMN CA.REV_INFO IS 'CA revocation information';
COMMENT ON COLUMN CA.CERTCHAIN IS 'Certificate chain without CA''s certificate';

ALTER TABLE CA ADD CONSTRAINT CONST_CA_NAME UNIQUE (NAME);

CREATE TABLE CAALIAS (
    NAME VARCHAR(45) NOT NULL,
    CA_ID SMALLINT NOT NULL,
    CONSTRAINT "CAALIAS_pkey" PRIMARY KEY (NAME)
);

CREATE TABLE CA_HAS_REQUESTOR (
    CA_ID SMALLINT NOT NULL,
    REQUESTOR_ID SMALLINT NOT NULL,
    PERMISSION INTEGER,
    PROFILES VARCHAR(500),
    CONSTRAINT "CA_HAS_REQUESTOR_pkey" PRIMARY KEY (CA_ID, REQUESTOR_ID)
);

CREATE TABLE CA_HAS_PUBLISHER (
    CA_ID SMALLINT NOT NULL,
    PUBLISHER_ID SMALLINT NOT NULL,
    CONSTRAINT "CA_HAS_PUBLISHER_pkey" PRIMARY KEY (CA_ID, PUBLISHER_ID)
);

CREATE TABLE CA_HAS_PROFILE (
    CA_ID SMALLINT NOT NULL,
    PROFILE_ID SMALLINT NOT NULL,
    ALIASES VARCHAR(100),
    CONSTRAINT "CA_HAS_PROFILE_pkey" PRIMARY KEY (CA_ID, PROFILE_ID)
);

-- changeset xipki:3
ALTER TABLE CA ADD CONSTRAINT FK_CA_CRL_SIGNER1
    FOREIGN KEY (CRL_SIGNER_NAME) REFERENCES SIGNER (NAME)
    ON UPDATE NO ACTION ON DELETE NO ACTION;

ALTER TABLE CAALIAS ADD CONSTRAINT FK_CAALIAS_CA1
    FOREIGN KEY (CA_ID) REFERENCES CA (ID)
    ON UPDATE NO ACTION ON DELETE CASCADE;

ALTER TABLE CA_HAS_REQUESTOR ADD CONSTRAINT FK_CA_HAS_REQUESTOR_REQUESTOR1
    FOREIGN KEY (REQUESTOR_ID) REFERENCES REQUESTOR (ID)
    ON UPDATE NO ACTION ON DELETE CASCADE;

ALTER TABLE CA_HAS_REQUESTOR ADD CONSTRAINT FK_CA_HAS_REQUESTOR_CA1
    FOREIGN KEY (CA_ID) REFERENCES CA (ID)
    ON UPDATE NO ACTION ON DELETE CASCADE;

ALTER TABLE CA_HAS_PUBLISHER ADD CONSTRAINT FK_CA_HAS_PUBLISHER_PUBLISHER1
    FOREIGN KEY (PUBLISHER_ID) REFERENCES PUBLISHER (ID)
    ON UPDATE NO ACTION ON DELETE CASCADE;

ALTER TABLE CA_HAS_PUBLISHER ADD CONSTRAINT FK_CA_HAS_PUBLISHER_CA1
    FOREIGN KEY (CA_ID) REFERENCES CA (ID)
    ON UPDATE NO ACTION ON DELETE CASCADE;

ALTER TABLE CA_HAS_PROFILE ADD CONSTRAINT FK_CA_HAS_PROFILE_PROFILE1
    FOREIGN KEY (PROFILE_ID) REFERENCES PROFILE (ID)
    ON UPDATE NO ACTION ON DELETE CASCADE;

ALTER TABLE CA_HAS_PROFILE ADD CONSTRAINT FK_CA_HAS_PROFILE_CA1
    FOREIGN KEY (CA_ID) REFERENCES CA (ID)
    ON UPDATE NO ACTION ON DELETE CASCADE;
