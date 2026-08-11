(ns clj-line.reader-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [clj-line.reader :as sut]))

(deftest complete?-test
  (testing "closed forms are complete"
    (is (sut/complete? ""))
    (is (sut/complete? "1"))
    (is (sut/complete? "foo"))
    (is (sut/complete? "(+ 1 2)"))
    (is (sut/complete? "[1 2 3]"))
    (is (sut/complete? "{:a 1}"))
    (is (sut/complete? "(foo) (bar)")))
  (testing "unclosed forms are incomplete"
    (is (not (sut/complete? "(")))
    (is (not (sut/complete? "(foo")))
    (is (not (sut/complete? "[1 2")))
    (is (not (sut/complete? "{:a 1")))
    (is (not (sut/complete? "\"unclosed string"))))
  (testing "invalid is treated as complete, eval will later surface the error"
    (is (sut/complete? "{:a 1})]"))))

(deftest read-all-test
  (testing "empty input"
    (is (= [] (sut/read-all ""))))
  (testing "single form"
    (is (= [42] (sut/read-all "42")))
    (is (= ['(+ 45 76)] (sut/read-all "(+ 45 76)"))))
  (testing "multiple top level forms"
    (is (= [1 2 3] (sut/read-all "1 2 3")))
    (is (= ['(+ 1 2) '(* 3 4)] (sut/read-all "(+ 1 2) (* 3 4)"))))
  (testing "nested forms"
    (is (= ['(let [x 1 y 2] (+ x y))] (sut/read-all "(let [x 1 y 2] (+ x y))"))))
  (testing "comments get skipped"
    (is (= ['foo] (sut/read-all ";; blah blah\nfoo")))))

(deftest skip-string-test
  (testing "advances past a closed string"
    (is (= 5 (sut/skip-string "\"abc\"" 0))))
  (testing "an escaped quote does not end the string"
    (is (= 6 (sut/skip-string "\"a\\\"b\"" 0))))
  (testing "returns len for an unterminated string"
    (is (= 4 (sut/skip-string "\"abc" 0)))))

(deftest bracket-scan-unmatched-test
  (testing "balanced inputs have no bad brackets"
    (is (= #{} (:unmatched (sut/bracket-scan ""))))
    (is (= #{} (:unmatched (sut/bracket-scan "(foo)"))))
    (is (= #{} (:unmatched (sut/bracket-scan "[1 2 3]"))))
    (is (= #{} (:unmatched (sut/bracket-scan "{:a 1}"))))
    (is (= #{} (:unmatched (sut/bracket-scan "(let [x 1] (* x 2))")))))
  (testing "unclosed openers are flagged at their position"
    (is (= #{0} (:unmatched (sut/bracket-scan "("))))
    (is (= #{0} (:unmatched (sut/bracket-scan "(foo"))))
    (is (= #{0 11} (:unmatched (sut/bracket-scan "(let [x 1] (foo")))))
  (testing "stray closers are flagged"
    (is (= #{0} (:unmatched (sut/bracket-scan ")"))))
    (is (= #{4} (:unmatched (sut/bracket-scan "(a) ]")))))
  (testing "mismatched type flags both open and close"
    (is (= #{0 2} (:unmatched (sut/bracket-scan "(a]")))))
  (testing "brackets inside strings are ignored"
    (is (= #{} (:unmatched (sut/bracket-scan "\"(\"")))))
  (testing "char literal consumes its char"
    (is (= #{} (:unmatched (sut/bracket-scan "\\("))))
    (is (= #{} (:unmatched (sut/bracket-scan "\\)"))))
    (is (= #{2} (:unmatched (sut/bracket-scan "\\;)")))))
  (testing "brackets after comments are ignored"
    (is (= #{} (:unmatched (sut/bracket-scan ";(")))))
  (testing "brackets in regex are ignored"
    (is (= #{} (:unmatched (sut/bracket-scan "#\"(\""))))))

(deftest bracket-scan-spans-test
  (testing "spans for each matched pair"
    (is (= [] (:spans (sut/bracket-scan ""))))
    (is (= [[0 5]] (:spans (sut/bracket-scan "(foo)"))))
    (is (= [[5 10] [11 18] [0 19]] (:spans (sut/bracket-scan "(let [x 1] (+ x 2))")))))
  (testing "unmatched brackets have no span"
    (is (= [] (:spans (sut/bracket-scan "(foo"))))
    (is (= [] (:spans (sut/bracket-scan "(a]")))))
  (testing " strings and comments do not gen create spans"
    (is (= [] (:spans (sut/bracket-scan "\"(\""))))
    (is (= [] (:spans (sut/bracket-scan ";("))))))

(deftest indent-column-test
  (testing "cursor not in form"
    (is (= 0 (sut/indent-column "" 0)))
    (is (= 0 (sut/indent-column "(foo)" 5)))
    (is (= 0 (sut/indent-column "[1 2 3]" 7))))
  (testing "cursor at the end of unclosed line"
    (is (= 1 (sut/indent-column "(foo" 4)))
    (is (= 1 (sut/indent-column "[1 2 3" 6)))
    (is (= 4 (sut/indent-column "abc(def" 7)))
    (is (= 12 (sut/indent-column "(let [x 1] (foo" 15))))
  (testing "paired mode curosr inside a balanced form"
    (is (= 1 (sut/indent-column "(+ 5 6)" 6)))
    (is (= 6 (sut/indent-column "(let [y 7])" 9)))
    (is (= 1 (sut/indent-column "(let [y 7])" 10))))
  (testing "cursor beyond balanced for does not indent"
    (is (= 1 (sut/indent-column "(foo (bar)" 10))))
  (testing "indent is relative to the current line"
    (is (= 2 (sut/indent-column "(let\n (foo" 10)))))

(deftest form-at-cursor-test
  (testing "innermost form the cursor is in"
    (is (= "(bar)" (sut/form-at-cursor "(foo (bar) baz)" 6)))
    (is (= "(foo (bar) baz)" (sut/form-at-cursor "(foo (bar) baz)" 1))))
  (testing "a complete buffer with no brackets"
    (is (= "foo" (sut/form-at-cursor "foo" 1))))
  (testing "unbalanced input"
    (is (= "(bar)" (sut/form-at-cursor "(foo (bar)" 6))))
  (testing "nothing complete at the cursor"
    (is (nil? (sut/form-at-cursor "(foo (bar)" 1)))))
