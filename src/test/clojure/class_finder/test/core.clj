(ns class-finder.test.core
  (:require [class-finder.core :as cf])
  (:use [clojure.test]))

(deftest test-find-class-by-name
  (is (contains? (cf/find-classes "Pattern") "java.util.regex.Pattern")))

(deftest test-find-class-by-name-in-package
  (let [results (cf/find-classes "List" "java.util")]
    (is (contains? results "java.util.List") "java.util.List not found")
    (is (not (contains? results "java.awt.List")) "java.awt.List should not be found")))

(deftest test-find-classes-by-glob
  (let [results (cf/find-classes "*List")]
    (is (contains? results "java.awt.List"))
    (is (contains? results "java.text.DigitList"))
    (is (contains? results "java.util.ArrayList"))
    (is (contains? results "java.util.LinkedList"))
    (is (contains? results "java.util.List"))))

(deftest test-find-classes-by-glob-in-package
  (let [results (cf/find-classes "*List" "java.util")]
    (is (not (contains? results "java.awt.List")))
    (is (not (contains? results "java.text.DigitList")))
    (is (contains? results "java.util.ArrayList"))
    (is (contains? results "java.util.LinkedList"))
    (is (contains? results "java.util.List"))))
