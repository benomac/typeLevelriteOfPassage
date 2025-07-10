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
    '$2a$10$hB2jCMCtBoxthyzV.Q4xAuKdkAJ7Z9ptPhugKCBM8fF3GnkI26eVq',
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
    '$2a$10$hKjCeqk3DIWZ1odm/.OiF.dGDoAtVSAPQzN7NmjogCkZ8IjTe7YwO',
    'Heath',
    'Emmott-Mcallister',
    'Rock the JVM',
    'RECRUITER'
)