-- Global counter: deliberately independent of calendar year and ticket IDs.
CREATE SEQUENCE dbo.ticket_number_sequence
    AS BIGINT
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    NO CYCLE
    CACHE 100;
