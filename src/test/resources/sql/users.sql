CREATE TABLE users (
    email text NOT NULL,
    hashedPassword text NOT NULL,
    firstName text,
    lastName text,
    company text,
    role text NOT NULL
);

ALTER TABLE users
ADD CONSTRAINT pk_users PRIMARY KEY (email);

INSERT INTO users (
    email,
    hashedPassword,
    firstName,
    lastName,
    company,
    role
) VALUES (
    'ben@rockthejvm.com',
    'rockthejvm',
    'Ben',
    'Mcallister',
    'Rock the JVM',
    'ADMIN'
);

INSERT INTO users (
    email,
    hashedPassword,
    firstName,
    lastName,
    company,
    role
) VALUES (
    'heath@rockthejvm.com',
    'heathisthebest',
    'Heath',
    'Emmott-Mcallister',
    'Rock the JVM',
    'RECRUITER'
)