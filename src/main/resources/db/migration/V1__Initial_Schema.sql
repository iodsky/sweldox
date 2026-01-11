-- Initial Schema Migration for Sweldox HRIS

-- Create department table
CREATE TABLE IF NOT EXISTS department (
    id VARCHAR(20) PRIMARY KEY,
    title VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by UUID,
    last_modified_by UUID,
    version BIGINT
);

-- Create position table
CREATE TABLE IF NOT EXISTS position (
    id VARCHAR(20) PRIMARY KEY,
    title VARCHAR(255) NOT NULL UNIQUE,
    department_id VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by UUID,
    last_modified_by UUID,
    version BIGINT,
    CONSTRAINT fk_position_department FOREIGN KEY (department_id) REFERENCES department(id)
);

-- Create deduction_type table
CREATE TABLE IF NOT EXISTS deduction_type (
    code VARCHAR(255) PRIMARY KEY,
    type VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by UUID,
    last_modified_by UUID,
    version BIGINT
);

-- Create benefit_type table
CREATE TABLE IF NOT EXISTS benefit_type (
    id VARCHAR(255) PRIMARY KEY,
    type VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by UUID,
    last_modified_by UUID,
    version BIGINT
);

-- Create employee sequence
CREATE SEQUENCE employee_id_seq START WITH 10001 INCREMENT BY 1;

-- Create employee table
CREATE TABLE IF NOT EXISTS employee (
    id BIGINT PRIMARY KEY DEFAULT nextval('employee_id_seq'),
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    birthday DATE,
    address VARCHAR(255) UNIQUE,
    phone_number VARCHAR(255) UNIQUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by UUID,
    last_modified_by UUID,
    version BIGINT
);

-- Create government_id table
CREATE TABLE IF NOT EXISTS government_id (
    id UUID PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    sss_no VARCHAR(255) UNIQUE,
    tin_no VARCHAR(255) UNIQUE,
    philhealth_no VARCHAR(255) UNIQUE,
    pagibig_no VARCHAR(255) UNIQUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by UUID,
    last_modified_by UUID,
    version BIGINT,
    CONSTRAINT fk_government_id_employee FOREIGN KEY (employee_id) REFERENCES employee(id)
);

-- Create employment_details table
CREATE TABLE IF NOT EXISTS employment_details (
    id UUID PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    supervisor_id BIGINT,
    position_id VARCHAR(20) NOT NULL,
    department_id VARCHAR(20) NOT NULL,
    status VARCHAR(50),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by UUID,
    last_modified_by UUID,
    version BIGINT,
    CONSTRAINT fk_employment_details_employee FOREIGN KEY (employee_id) REFERENCES employee(id),
    CONSTRAINT fk_employment_details_supervisor FOREIGN KEY (supervisor_id) REFERENCES employee(id),
    CONSTRAINT fk_employment_details_position FOREIGN KEY (position_id) REFERENCES position(id),
    CONSTRAINT fk_employment_details_department FOREIGN KEY (department_id) REFERENCES department(id)
);

-- Create compensation table
CREATE TABLE IF NOT EXISTS compensation (
    id UUID PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    basic_salary NUMERIC(19, 2),
    hourly_rate NUMERIC(19, 2),
    semi_monthly_rate NUMERIC(19, 2),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by UUID,
    last_modified_by UUID,
    version BIGINT,
    CONSTRAINT fk_compensation_employee FOREIGN KEY (employee_id) REFERENCES employee(id)
);

-- Create benefit table
CREATE TABLE IF NOT EXISTS benefit (
    id UUID PRIMARY KEY,
    compensation_id UUID NOT NULL,
    benefit_type_id VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 2),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by UUID,
    last_modified_by UUID,
    version BIGINT,
    CONSTRAINT fk_benefit_compensation FOREIGN KEY (compensation_id) REFERENCES compensation(id),
    CONSTRAINT fk_benefit_type FOREIGN KEY (benefit_type_id) REFERENCES benefit_type(id)
);

-- Create user_role table
CREATE TABLE IF NOT EXISTS user_role (
    role VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by UUID,
    last_modified_by UUID,
    version BIGINT
);

-- Create users table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    email VARCHAR(255) UNIQUE,
    password VARCHAR(255),
    role_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by UUID,
    last_modified_by UUID,
    version BIGINT,
    CONSTRAINT fk_users_employee FOREIGN KEY (employee_id) REFERENCES employee(id),
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES user_role(role)
);

-- Add foreign keys for created_by and last_modified_by in department
ALTER TABLE department
    ADD CONSTRAINT fk_department_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    ADD CONSTRAINT fk_department_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES users(id);

-- Add foreign keys for created_by and last_modified_by in position
ALTER TABLE position
    ADD CONSTRAINT fk_position_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    ADD CONSTRAINT fk_position_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES users(id);

-- Add foreign keys for created_by and last_modified_by in employee
ALTER TABLE employee
    ADD CONSTRAINT fk_employee_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    ADD CONSTRAINT fk_employee_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES users(id);

-- Add foreign keys for created_by and last_modified_by in government_id
ALTER TABLE government_id
    ADD CONSTRAINT fk_government_id_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    ADD CONSTRAINT fk_government_id_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES users(id);

-- Add foreign keys for created_by and last_modified_by in employment_details
ALTER TABLE employment_details
    ADD CONSTRAINT fk_employment_details_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    ADD CONSTRAINT fk_employment_details_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES users(id);

-- Add foreign keys for created_by and last_modified_by in compensation
ALTER TABLE compensation
    ADD CONSTRAINT fk_compensation_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    ADD CONSTRAINT fk_compensation_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES users(id);

-- Add foreign keys for created_by and last_modified_by in benefit
ALTER TABLE benefit
    ADD CONSTRAINT fk_benefit_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    ADD CONSTRAINT fk_benefit_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES users(id);

-- Add foreign keys for created_by and last_modified_by in user_role
ALTER TABLE user_role
    ADD CONSTRAINT fk_user_role_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    ADD CONSTRAINT fk_user_role_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES users(id);

-- Add foreign keys for created_by and last_modified_by in users
ALTER TABLE users
    ADD CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    ADD CONSTRAINT fk_users_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES users(id);

-- Add foreign keys for created_by and last_modified_by in deduction_type
ALTER TABLE deduction_type
    ADD CONSTRAINT fk_deduction_type_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    ADD CONSTRAINT fk_deduction_type_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES users(id);

-- Add foreign keys for created_by and last_modified_by in benefit_type
ALTER TABLE benefit_type
    ADD CONSTRAINT fk_benefit_type_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    ADD CONSTRAINT fk_benefit_type_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES users(id);

-- Create attendance table
CREATE TABLE IF NOT EXISTS attendance (
    id UUID PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    date DATE,
    time_in TIME,
    time_out TIME,
    total_hours NUMERIC(19, 2),
    overtime NUMERIC(19, 2),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by UUID,
    last_modified_by UUID,
    version BIGINT,
    CONSTRAINT fk_attendance_employee FOREIGN KEY (employee_id) REFERENCES employee(id),
    CONSTRAINT fk_attendance_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_attendance_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES users(id),
    CONSTRAINT uk_attendance_employee_date UNIQUE (employee_id, date)
);

-- Create leave_credit table
CREATE TABLE IF NOT EXISTS leave_credit (
    id UUID PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    type VARCHAR(50),
    credits DOUBLE PRECISION NOT NULL,
    fiscal_year VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by UUID,
    last_modified_by UUID,
    version BIGINT,
    CONSTRAINT fk_leave_credit_employee FOREIGN KEY (employee_id) REFERENCES employee(id),
    CONSTRAINT fk_leave_credit_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_leave_credit_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES users(id),
    CONSTRAINT chk_leave_credit_positive CHECK (credits >= 0)
);

-- Create leave_request table
CREATE TABLE IF NOT EXISTS leave_request (
    id VARCHAR(255) PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    leave_type VARCHAR(50),
    start_date DATE,
    end_date DATE,
    note TEXT,
    leave_status VARCHAR(50),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by UUID,
    last_modified_by UUID,
    version BIGINT,
    CONSTRAINT fk_leave_request_employee FOREIGN KEY (employee_id) REFERENCES employee(id),
    CONSTRAINT fk_leave_request_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_leave_request_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES users(id),
    CONSTRAINT uk_leave_request_employee_dates UNIQUE (employee_id, start_date, end_date)
);

-- Create payroll table
CREATE TABLE IF NOT EXISTS payroll (
    id UUID PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    period_start_date DATE,
    period_end_date DATE,
    pay_date DATE,
    days_worked INTEGER,
    overtime NUMERIC(19, 2),
    monthly_rate NUMERIC(19, 2),
    daily_rate NUMERIC(19, 2),
    gross_pay NUMERIC(19, 2),
    total_benefits NUMERIC(19, 2),
    total_deductions NUMERIC(19, 2),
    net_pay NUMERIC(19, 2),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by UUID,
    last_modified_by UUID,
    version BIGINT,
    CONSTRAINT fk_payroll_employee FOREIGN KEY (employee_id) REFERENCES employee(id),
    CONSTRAINT fk_payroll_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_payroll_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES users(id),
    CONSTRAINT uk_payroll_employee_period UNIQUE (employee_id, period_start_date, period_end_date)
);

-- Create deduction table
CREATE TABLE IF NOT EXISTS deduction (
    id UUID PRIMARY KEY,
    payroll_id UUID NOT NULL,
    deduction_code VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 2),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by UUID,
    last_modified_by UUID,
    version BIGINT,
    CONSTRAINT fk_deduction_payroll FOREIGN KEY (payroll_id) REFERENCES payroll(id),
    CONSTRAINT fk_deduction_type FOREIGN KEY (deduction_code) REFERENCES deduction_type(code),
    CONSTRAINT fk_deduction_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_deduction_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES users(id)
);

-- Create payroll_benefits table
CREATE TABLE IF NOT EXISTS payroll_benefits (
    id UUID PRIMARY KEY,
    payroll_id UUID NOT NULL,
    benefit_type_id VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 2),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by UUID,
    last_modified_by UUID,
    version BIGINT,
    CONSTRAINT fk_payroll_benefits_payroll FOREIGN KEY (payroll_id) REFERENCES payroll(id),
    CONSTRAINT fk_payroll_benefits_type FOREIGN KEY (benefit_type_id) REFERENCES benefit_type(id),
    CONSTRAINT fk_payroll_benefits_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_payroll_benefits_last_modified_by FOREIGN KEY (last_modified_by) REFERENCES users(id)
);

-- Create indexes for better query performance
CREATE INDEX idx_employee_first_name ON employee(first_name);
CREATE INDEX idx_employee_last_name ON employee(last_name);
CREATE INDEX idx_attendance_date ON attendance(date);
CREATE INDEX idx_attendance_employee_date ON attendance(employee_id, date);
CREATE INDEX idx_leave_request_employee ON leave_request(employee_id);
CREATE INDEX idx_leave_request_dates ON leave_request(start_date, end_date);
CREATE INDEX idx_payroll_employee ON payroll(employee_id);
CREATE INDEX idx_payroll_period ON payroll(period_start_date, period_end_date);