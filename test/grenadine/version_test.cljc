(ns grenadine.version-test
  (:require [clojure.test :refer [deftest is]]
            [grenadine.test-support :refer [throws?]]
            [grenadine.version :as version]))

(deftest compares-maven-versions
  (doseq [[left right]
          [["1" "2"]
           ["1.2.3" "1.2.10"]
           ["1.9.0" "1.10.0"]
           ["1.0-alpha" "1.0-beta"]
           ["1.0-beta" "1.0-rc1"]
           ["1.0-rc1" "1.0"]
           ["1.0-SNAPSHOT" "1.0"]
           ["1.0-alpha1" "1.0-alpha2"]
           ["1.0" "1.0.1"]
           ["1.0" "1.0-sp"]
           ["1.0" "1.0-sp1"]
           ["0.9" "1.0"]
           ["1.0a1" "1.0"]
           ["1.0.9" "1.0.10"]
           ["1.0.99999999999999999999" "1.0.100000000000000000000"]]]
    (is (neg? (version/compare-versions left right))
        (str left " should precede " right)))
  (is (zero? (version/compare-versions "1" "1.0.0")))
  (is (zero? (version/compare-versions "1-1" "1.0-1")))
  (is (neg? (version/compare-versions "1-1" "1.0.1")))
  (is (zero? (version/compare-versions "1.0-final" "1.0")))
  (is (zero? (version/compare-versions "1.0-ga" "1.0.0-release"))))

(deftest parses-and-matches-maven-version-ranges
  (is (version/version-range? "[1.0,2.0)"))
  (is (not (version/version-range? "1.0")))
  (is (version/in-range? "1.0" "[1.0,2.0)"))
  (is (version/in-range? "1.9" "[1.0,2.0)"))
  (is (not (version/in-range? "2.0" "[1.0,2.0)")))
  (is (version/in-range? "1.0" "[1.0]"))
  (is (= "1.0" (version/exact-range-version "[1.0]")))
  (is (nil? (version/exact-range-version "[1.0,2.0)")))
  (is (not (version/in-range? "1.0.1" "[1.0]")))
  (is (version/in-range? "0.9" "(,1.0],[1.2,)"))
  (is (not (version/in-range? "1.1" "(,1.0],[1.2,)")))
  (is (version/in-range? "1.2" "(,1.0],[1.2,)"))
  (is (throws? (version/parse-version-range "[1,2,3]")))
  (is (throws? (version/parse-version-range "(,)")))
  (is (throws? (version/parse-version-range "[1,2")))
  (is (throws? (version/parse-version-range "[1,2],")))
  (is (throws? (version/parse-version-range "[1,2][3,4]"))))

(deftest maps-timestamped-snapshots-to-their-base-directory
  (is (= "0.0.1-SNAPSHOT"
         (version/snapshot-base-version "0.0.1-20200715.082439-21")))
  (is (= "1.0.0"
         (version/snapshot-base-version "1.0.0"))))
