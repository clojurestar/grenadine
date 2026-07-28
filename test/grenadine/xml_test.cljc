(ns grenadine.xml-test
  (:require [clojure.test :refer [deftest is testing]]
            [grenadine.test-support :refer [throws?]]
            [grenadine.xml :as xml]))

(deftest parses-pom-shaped-xml
  (is
   (=
    {:tag :project
     :attrs {:modelVersion "4.0.0"}
     :content
     ["\n  "
      {:tag :groupId :attrs {} :content ["org.example"]}
      "\n  "
      {:tag :artifactId :attrs {} :content ["demo"]}
      "\n  "
      {:tag :properties
       :attrs {}
       :content
       [{:tag :message :attrs {} :content ["one & two"]}]}
      "\n"]}
    (xml/parse
     (str "<?xml version=\"1.0\"?>"
          "<project modelVersion=\"4.0.0\">\n"
          "  <groupId>org.example</groupId>\n"
          "  <artifactId>demo</artifactId>\n"
          "  <properties><message>one &amp; two</message></properties>\n"
          "</project>")))))

(deftest handles-namespaces-comments-and-character-references
  (is
   (=
    {:tag :project
     :attrs {:schemaLocation "somewhere"}
     :content
     [{:tag :name :attrs {} :content ["AAB"]}]}
    (xml/parse
     (str "<m:project xmlns:xsi=\"ignored\" xsi:schemaLocation=\"somewhere\">"
          "<!-- comment --><m:name>&#65;&#x41;B</m:name></m:project>")))))

(deftest handles-self-closing-elements
  (is
   (=
    {:tag :project
     :attrs {}
     :content [{:tag :relativePath :attrs {} :content []}]}
    (xml/parse "<project><relativePath /></project>"))))

(deftest rejects-unsafe-and-malformed-input
  (testing "external declarations"
    (is (throws? (xml/parse "<!DOCTYPE project><project/>"))))
  (testing "mismatched tags"
    (is (throws? (xml/parse "<project><name></project>"))))
  (testing "unknown entities"
    (is (throws? (xml/parse "<project>&nope;</project>"))))
  (testing "duplicate attributes"
    (is (throws? (xml/parse "<project x=\"1\" x=\"2\"/>")))))
