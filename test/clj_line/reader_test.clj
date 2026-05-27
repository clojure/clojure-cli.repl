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

(deftest unmatched-brackets-test
  (testing "balanced inputs have no bad brackets"
    (is (= #{} (sut/unmatched-brackets "")))
    (is (= #{} (sut/unmatched-brackets "(foo)")))
    (is (= #{} (sut/unmatched-brackets "[1 2 3]")))
    (is (= #{} (sut/unmatched-brackets "{:a 1}")))
    (is (= #{} (sut/unmatched-brackets "(let [x 1] (* x 2))"))))
  (testing "unclosed openers are flagged at their position"
    (is (= #{0} (sut/unmatched-brackets "(")))
    (is (= #{0} (sut/unmatched-brackets "(foo")))
    (is (= #{0 11} (sut/unmatched-brackets "(let [x 1] (foo"))))
  (testing "stray closers are flagged"
    (is (= #{0} (sut/unmatched-brackets ")")))
    (is (= #{4} (sut/unmatched-brackets "(a) ]"))))
  (testing "mismatched type flags both open and close"
    (is (= #{0 2} (sut/unmatched-brackets "(a]"))))
  (testing "brackets inside strings are ignored"
    (is (= #{} (sut/unmatched-brackets "\"(\""))))
  (testing "char literal consumes its char"
    (is (= #{} (sut/unmatched-brackets "\\(")))
    (is (= #{} (sut/unmatched-brackets "\\)")))
    (is (= #{2} (sut/unmatched-brackets "\\;)"))))
  (testing "brackets after comments are ignored"
    (is (= #{} (sut/unmatched-brackets ";("))))
  (testing "brackets in regex are ignored"
    (is (= #{} (sut/unmatched-brackets "#\"(\"")))))

(deftest indent-column-test
  (testing "balanced inputs return zero"
    (is (= 0 (sut/indent-column "")))
    (is (= 0 (sut/indent-column "(foo)")))
    (is (= 0 (sut/indent-column "[1 2 3]"))))
  (testing "single unclosed opener at start of buffer"
    (is (= 1 (sut/indent-column "(foo")))
    (is (= 1 (sut/indent-column "[1 2 3"))))
  (testing "unclosed opener with leading content"
    (is (= 4 (sut/indent-column "abc(def"))))
  (testing "nested openers indent to the innermost"
    (is (= 12 (sut/indent-column "(let [x 1] (foo"))))
  (testing "indent is relative to the current line"
    (is (= 2 (sut/indent-column "(let\n (foo")))))
