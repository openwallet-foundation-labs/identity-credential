import cbor
import logging
import sqlite3

import util

logger = logging.getLogger("mdl-ref-server.database")


class DatabaseError(Exception):
    def __init__(self, message):
        Exception.__init__(self, message)


class SystemOfRecord:
    def __init__(self, database_file_path):
        self.db = sqlite3.connect(database_file_path)

        c = self.db.cursor()
        c.execute("PRAGMA foreign_keys = ON;")

        c.execute("""
CREATE TABLE IF NOT EXISTS persons (
    person_id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    portrait BLOB NOT NULL
);
""")

        c.execute("""
CREATE TABLE IF NOT EXISTS documents (
    document_id INTEGER PRIMARY KEY,
    person_id INTEGER NOT NULL,
    doc_type TEXT NOT NULL,
    access_control_profiles BLOB NOT NULL,
    name_spaces BLOB NOT NULL,
    data_timestamp REAL NOT NULL,

    FOREIGN KEY (person_id)
      REFERENCES persons (person_id)
);
""")

        # TODO: include details of whether provisioning_code can be
        # reused, what kind of proofing is required etc.
        c.execute("""
CREATE TABLE IF NOT EXISTS issued_documents (
    issued_document_id INTEGER PRIMARY KEY,
    document_id INTEGER NOT NULL,
    provisioning_code TEXT NOT NULL,

    FOREIGN KEY (document_id)
      REFERENCES documents (document_id)
);
""")

        c.execute("""
CREATE TABLE IF NOT EXISTS configured_documents (
    configured_document_id INTEGER PRIMARY KEY,
    issued_document_id INTEGER NOT NULL,
    credential_key_x509_cert_chain BLOB,
    encoded_cose_credential_key BLOB,
    proof_of_provisioning BLOB,
    last_updated_timestamp REAL,
    data_timestamp REAL NOT NULL,
    status TEXT,

    FOREIGN KEY (issued_document_id)
      REFERENCES issued_documents (issued_document_id)
);
""")

        c.execute("""
CREATE TABLE IF NOT EXISTS endorsed_authentication_keys (
    endorsed_authentication_key_id INTEGER PRIMARY KEY,
    configured_document_id INTEGER NOT NULL,
    authentication_key_x509_cert BLOB,
    static_auth_datas BLOB,
    generated_at_timestamp REAL NOT NULL,
    expires_at_timestamp REAL NOT NULL,

    FOREIGN KEY (configured_document_id)
      REFERENCES configured_documents (configured_document_id)
);
""")
        self.db.commit()

    def get_sqlite3(self):
        return self.db

    def lookup_persons(self):
        c = self.db.cursor()
        c.execute("""
SELECT person_id,
       name,
       portrait
       FROM persons""")
        persons = []
        for person_data in c.fetchall():
            persons.append(Person(person_data))
        return persons

    def lookup_document_ids_by_person_id(self, person_id):
        c = self.db.cursor()
        c.execute("""
SELECT document_id
       FROM documents
WHERE person_id = ?;
""",
                  (person_id,))
        document_ids = []
        for data in c.fetchall():
            document_ids.append(data[0])
        return document_ids

    def lookup_issued_document_ids_by_document_id(self, document_id):
        c = self.db.cursor()
        c.execute("""
SELECT issued_document_id
       FROM issued_documents
WHERE document_id = ?;
""",
                  (document_id,))
        issued_document_ids = []
        for data in c.fetchall():
            issued_document_ids.append(data[0])
        return issued_document_ids

    def lookup_person_by_person_id(self, person_id):
        c = self.db.cursor()
        c.execute("""
SELECT person_id,
       name,
       portrait
       FROM persons
WHERE person_id = ? LIMIT 1;
""",
                  (person_id,))
        data = c.fetchone()
        if not data:
            raise DatabaseError("No person for given person_id")
        return Person(data)

    def lookup_document_by_document_id(self, document_id):
        c = self.db.cursor()
        c.execute("""
SELECT document_id,
       person_id,
       doc_type,
       access_control_profiles,
       name_spaces,
       data_timestamp
       FROM documents
WHERE document_id = ? LIMIT 1;
""",
                  (document_id,))
        data = c.fetchone()
        if not data:
            raise DatabaseError("No document for given document_id")
        return Document(data)

    def lookup_issued_document_by_provisioning_code(self, provisioning_code):
        c = self.db.cursor()
        c.execute("""
SELECT issued_document_id,
       document_id,
       provisioning_code
       FROM issued_documents
WHERE provisioning_code = ? LIMIT 1;
""",
                  (provisioning_code,))
        data = c.fetchone()
        if not data:
            raise DatabaseError("No issued_document for given provisioning_code")
        return IssuedDocument(data)

    def lookup_issued_document_by_issued_document_id(self, issued_document_id):
        c = self.db.cursor()
        c.execute("""
SELECT issued_document_id,
       document_id,
       provisioning_code
       FROM issued_documents
WHERE issued_document_id = ? LIMIT 1;
""",
                  (issued_document_id,))
        data = c.fetchone()
        if not data:
            raise DatabaseError("No issued_document for given issued_document_id")
        return IssuedDocument(data)

    def lookup_configured_document_by_credential_key(self, credential_key):
        encoded_cose_credential_key = cbor.dumps(util.to_cose_key(credential_key))
        c = self.db.cursor()
        c.execute("""
SELECT configured_document_id,
       issued_document_id,
       credential_key_x509_cert_chain,
       proof_of_provisioning,
       last_updated_timestamp,
       data_timestamp,
       status
 FROM configured_documents
WHERE encoded_cose_credential_key = ? LIMIT 1;
""",
                  (encoded_cose_credential_key,))
        data = c.fetchone()
        if not data:
            raise DatabaseError("No configured_document for given credential_key")
        return ConfiguredDocument(data)

    def lookup_configured_document_ids_by_issued_document_id(self, issued_document_id):
        c = self.db.cursor()
        c.execute("""
SELECT configured_document_id
       FROM configured_documents
WHERE issued_document_id = ?;
""",
                  (issued_document_id,))
        configured_document_ids = []
        for data in c.fetchall():
            configured_document_ids.append(data[0])
        return configured_document_ids

    def add_configured_documents_entry(self,
                                       issued_document_id,
                                       credential_key_x509_cert_chain,
                                       proof_of_provisioning,
                                       last_updated_timestamp,
                                       data_timestamp):
        public_key = util.cert_chain_get_public_key(credential_key_x509_cert_chain)
        encoded_cose_credential_key = cbor.dumps(util.to_cose_key(public_key))
        c = self.db.cursor()
        c.execute("""
INSERT INTO configured_documents (configured_document_id,
                                 issued_document_id,
                                 credential_key_x509_cert_chain,
                                 encoded_cose_credential_key,
                                 proof_of_provisioning,
                                 last_updated_timestamp,
                                 data_timestamp)
VALUES (NULL, ?, ?, ?, ?, ?, ?)
""", (issued_document_id,
      credential_key_x509_cert_chain,
      encoded_cose_credential_key,
      proof_of_provisioning,
      last_updated_timestamp,
      data_timestamp))

    def update_configured_documents_entry(self,
                                          configured_document_id,
                                          proof_of_provisioning,
                                          last_updated_timestamp,
                                          data_timestamp):
        c = self.db.cursor()
        c.execute("""
UPDATE configured_documents 
SET    proof_of_provisioning = ?,
       last_updated_timestamp = ?,
       data_timestamp = ?
WHERE configured_document_id = ?
""", (proof_of_provisioning,
      last_updated_timestamp,
      data_timestamp,
      configured_document_id))

    def update_configured_documents_status(self,
                                           configured_document_id,
                                           status):
        c = self.db.cursor()
        c.execute("""
UPDATE configured_documents 
SET    status = ?
WHERE configured_document_id = ?
""", (status,
      configured_document_id))

    def update_document_entry(self,
                              document_id,
                              name_spaces,
                              data_timestamp):
        c = self.db.cursor()
        c.execute("""
UPDATE documents 
SET    name_spaces = ?,
       data_timestamp = ?
WHERE document_id = ?
""", (name_spaces,
      data_timestamp,
      document_id))

    def delete_configured_documents_entry(self,
                                          configured_document_id):
        c = self.db.cursor()
        c.execute("""
DELETE FROM configured_documents 
WHERE configured_document_id = ?
""", (configured_document_id,))

    def commit(self):
        self.db.commit()


class Person:
    def __init__(self, data):
        self.person_id = data[0]
        self.name = data[1]
        self.portrait = data[2]


class Document:
    def __init__(self, data):
        self.document_id = data[0]
        self.person_id = data[1]
        self.doc_type = data[2]
        self.access_control_profiles = data[3]
        self.name_spaces = data[4]
        self.data_timestamp = data[5]


class IssuedDocument:
    def __init__(self, data):
        self.issued_document_id = data[0]
        self.document_id = data[1]
        self.provisioning_code = data[2]


class ConfiguredDocument:
    def __init__(self, data):
        self.configured_document_id = data[0]
        self.issued_document_id = data[1]
        self.credential_key_x509_cert_chain = data[2]
        self.proof_of_provisioning = data[3]
        self.last_updated_timestamp = data[4]
        self.data_timestamp = data[5]
        self.status = data[6]
