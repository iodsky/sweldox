-- Insert reference data

-- Insert departments
INSERT INTO department (id, title, created_at, updated_at, version) VALUES
                                                                        ('IT', 'INFORMATION TECHNOLOGY', NOW(), NOW(), 0),
                                                                        ('HR', 'HUMAN RESOURCES', NOW(), NOW(), 0),
                                                                        ('CORP', 'CORPORATE', NOW(), NOW(), 0),
                                                                        ('ACC', 'ACCOUNTING', NOW(), NOW(), 0),
                                                                        ('PAY', 'PAYROLL', NOW(), NOW(), 0),
                                                                        ('SAL', 'SALES', NOW(), NOW(), 0),
                                                                        ('LOG', 'LOGISTICS', NOW(), NOW(), 0),
                                                                        ('CS', 'CUSTOMER SERVICE', NOW(), NOW(), 0);

-- Insert positions
INSERT INTO position (id, title, department_id, created_at, updated_at, version) VALUES
                                                                                     ('CEO', 'Chief Executive Officer', 'CORP', NOW(), NOW(), 0),
                                                                                     ('COO', 'Chief Operating Officer', 'CORP', NOW(), NOW(), 0),
                                                                                     ('CFO', 'Chief Finance Officer', 'CORP', NOW(), NOW(), 0),
                                                                                     ('CMO', 'Chief Marketing Officer', 'CORP', NOW(), NOW(), 0),
                                                                                     ('CSR', 'Customer Service and Relations', 'CS', NOW(), NOW(), 0),
                                                                                     ('ITOPSYS', 'IT Operations and Systems', 'IT', NOW(), NOW(), 0),
                                                                                     ('HRM', 'HR Manager', 'HR', NOW(), NOW(), 0),
                                                                                     ('HRTL', 'HR Team Leader', 'HR', NOW(), NOW(), 0),
                                                                                     ('HRRL', 'HR Rank and File', 'HR', NOW(), NOW(), 0),
                                                                                     ('ACCHEAD', 'Accounting Head', 'ACC', NOW(), NOW(), 0),
                                                                                     ('ACCMNGR', 'Account Manager', 'ACC', NOW(), NOW(), 0),
                                                                                     ('ACCTL', 'Account Team Leader', 'ACC', NOW(), NOW(), 0),
                                                                                     ('ACCRL', 'Account Rank and File', 'ACC', NOW(), NOW(), 0),
                                                                                     ('PAYRMNGR', 'Payroll Manager', 'PAY', NOW(), NOW(), 0),
                                                                                     ('PAYRL', 'Payroll Rank and File', 'PAY', NOW(), NOW(), 0),
                                                                                     ('PAYTL', 'Payroll Team Leader', 'PAY', NOW(), NOW(), 0),
                                                                                     ('SCL', 'Supply Chain and Logistics', 'LOG', NOW(), NOW(), 0),
                                                                                     ('SLMKT', 'Sales and Marketing', 'SAL', NOW(), NOW(), 0);

-- Insert deduction types
INSERT INTO deduction_type (code, type, created_at, updated_at, version) VALUES
                                                                             ('SSS', 'Social Security System', NOW(), NOW(), 0),
                                                                             ('PHIC', 'PhilHealth', NOW(), NOW(), 0),
                                                                             ('HDMF', 'Pag-IBIG', NOW(), NOW(), 0),
                                                                             ('TAX', 'Withholding Tax', NOW(), NOW(), 0);

-- Insert benefit types
INSERT INTO benefit_type (id, type, created_at, updated_at, version) VALUES
                                                                         ('MEAL', 'MEAL ALLOWANCE', NOW(), NOW(), 0),
                                                                         ('PHONE', 'PHONE ALLOWANCE', NOW(), NOW(), 0),
                                                                         ('CLOTHING', 'CLOTHING ALLOWANCE', NOW(), NOW(), 0);

-- Insert user roles
INSERT INTO user_role (role, created_at, updated_at, version) VALUES
                                                                  ('IT', NOW(), NOW(), 0),
                                                                  ('HR', NOW(), NOW(), 0),
                                                                  ('PAYROLL', NOW(), NOW(), 0),
                                                                  ('EMPLOYEE', NOW(), NOW(), 0);

-- Insert super user employee
INSERT INTO employee (id, first_name, last_name, birthday, address, phone_number, created_at, updated_at, version) VALUES
    (10000, 'Super', 'User', '1990-01-15', '123 Test Street, Manila, Philippines', '+639171234567', NOW(), NOW(), 0);

-- Insert government ID for super user
INSERT INTO government_id (id, employee_id, sss_no, tin_no, philhealth_no, pagibig_no, created_at, updated_at, version) VALUES
    ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 10000, '34-1234567-8', '123-456-789-000', '12-345678901-2', '1234-5678-9012', NOW(), NOW(), 0);

-- Insert employment details for super user
INSERT INTO employment_details (id, employee_id, supervisor_id, position_id, department_id, status, created_at, updated_at, version) VALUES
    ('b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 10000, NULL, 'ITOPSYS', 'IT', 'REGULAR', NOW(), NOW(), 0);

-- Insert compensation for super user
INSERT INTO compensation (id, employee_id, basic_salary, hourly_rate, semi_monthly_rate, created_at, updated_at, version) VALUES
    ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 10000, 50000.00, 297.62, 25000.00, NOW(), NOW(), 0);

-- Insert super user account (password is 'password' encoded with bcrypt)
INSERT INTO users (id, employee_id, email, password, role_id, created_at, updated_at, version) VALUES
    ('d0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 10000, 'superuser@sweldox.com', '$2a$12$lMIUx49rQdGhsrfLbQB3Hetueio4UgmdWV/Vcw3KweucDgZ6fDs/a', 'IT', NOW(), NOW(), 0)
    ON CONFLICT (id) DO NOTHING;
