CREATE TABLE Books (
    book_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    isbn VARCHAR(13),
    title VARCHAR(255) NOT NULL,
    author VARCHAR(255) NOT NULL,
    publisher VARCHAR(255),
    publication_year INT
);

CREATE TABLE Accounts (
    account_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (
        role IN ('EMPLOYEE', 'BORROWER')
    )
);

CREATE TABLE BookCopies (
    copy_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    book_id BIGINT NOT NULL REFERENCES Books(book_id),
    copy_number INT NOT NULL,

    UNIQUE (book_id, copy_number)
);

CREATE TABLE Borrows (
    checkout_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    copy_id BIGINT NOT NULL REFERENCES BookCopies(copy_id),
    account_id BIGINT NOT NULL REFERENCES Accounts(account_id),
    checkout_date DATE NOT NULL DEFAULT CURRENT_DATE,
    due_date DATE NOT NULL,
    return_date DATE
);
