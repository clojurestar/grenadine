(ns grenadine.pom-test
  (:require [clojure.test :refer [deftest is testing]]
            [grenadine.test-support :refer [throws?]]
            [grenadine.pom :as pom]))

(def parent-pom
  "<project>
     <modelVersion>4.0.0</modelVersion>
     <groupId>com.example</groupId>
     <artifactId>parent</artifactId>
     <version>1</version>
     <properties>
       <managed.version>2.1</managed.version>
       <shared.value>parent</shared.value>
     </properties>
     <dependencyManagement><dependencies>
       <dependency>
         <groupId>demo</groupId><artifactId>managed</artifactId>
         <version>${managed.version}</version>
       </dependency>
     </dependencies></dependencyManagement>
     <dependencies>
       <dependency>
         <groupId>demo</groupId><artifactId>inherited</artifactId>
         <version>1.0</version>
       </dependency>
     </dependencies>
   </project>")

(def bom-pom
  "<project>
     <modelVersion>4.0.0</modelVersion>
     <groupId>demo</groupId><artifactId>bom</artifactId><version>3</version>
     <packaging>pom</packaging>
     <dependencyManagement><dependencies>
       <dependency>
         <groupId>demo</groupId><artifactId>from-bom</artifactId>
         <version>4.2</version>
       </dependency>
     </dependencies></dependencyManagement>
   </project>")

(def child-pom
  "<project>
     <modelVersion>4.0.0</modelVersion>
     <parent>
       <groupId>com.example</groupId><artifactId>parent</artifactId><version>1</version>
     </parent>
     <artifactId>child</artifactId>
     <properties><shared.value>child</shared.value></properties>
     <dependencyManagement><dependencies>
       <dependency>
         <groupId>demo</groupId><artifactId>bom</artifactId><version>3</version>
         <type>pom</type><scope>import</scope>
       </dependency>
       <dependency>
         <groupId>demo</groupId><artifactId>managed</artifactId><version>2.2</version>
       </dependency>
     </dependencies></dependencyManagement>
     <dependencies>
       <dependency><groupId>demo</groupId><artifactId>managed</artifactId></dependency>
       <dependency><groupId>demo</groupId><artifactId>from-bom</artifactId></dependency>
     </dependencies>
   </project>")

(defn fixture-fetch
  [{:keys [group artifact version]}]
  (get {["com.example" "parent" "1"] parent-pom
        ["com.example" "child" "1"] child-pom
        ["demo" "bom" "3"] bom-pom}
       [group artifact version]))

(deftest parses-raw-pom
  (let [raw (pom/parse-pom child-pom)]
    (is (= {:group nil :artifact "child" :version nil}
           (:declared-coords raw)))
    (is (= 2 (count (:dependency-management raw))))
    (is (= 2 (count (:dependencies raw))))))

(deftest builds-effective-pom
  (let [effective
        (pom/effective-pom
         {:group "com.example" :artifact "child" :version "1"}
         fixture-fetch)]
    (is (= {:group "com.example" :artifact "child" :version "1"}
           (:coords effective)))
    (is (= "child" (get-in effective [:properties "shared.value"])))
    (is (= ["1.0" "2.2" "4.2"]
           (mapv :version (:deps effective))))
    (is (= ["inherited" "managed" "from-bom"]
           (mapv :artifact (:deps effective))))))

(deftest detects-property-cycles
  (is (throws?
       (pom/interpolate-string
        "${a}" {"a" "${b}" "b" "${a}"}))))

(deftest detects-model-cycles
  (let [cyclic
        "<project><modelVersion>4.0.0</modelVersion>
         <parent><groupId>x</groupId><artifactId>a</artifactId><version>1</version></parent>
         <groupId>x</groupId><artifactId>a</artifactId><version>1</version>
         </project>"]
    (is (throws?
         (pom/effective-pom
          {:group "x" :artifact "a" :version "1"}
          (fn [_] cyclic))))))
