(ns hodur-engine.utils-test
  (:require [camel-snake-kebab.core :refer [->camelCaseKeyword
                                            ->PascalCaseKeyword
                                            ->kebab-case-keyword
                                            ->snake_case_keyword]]
            [clojure.test :refer :all]
            [datascript.core :as d]
            [hodur-engine.core :as engine]
            [hodur-engine.utils :as utils]))

(deftest ^:wip acyclical-topological-sort-test
  (let [meta-db (engine/init-schema
                 '[A
                   [^String af1
                    ^B af2
                    ^C af3
                    [^Integer af3p1
                     ^C af3p2]]

                   B
                   [^C bf1]

                   C
                   [^B cf1]])
        sorted (utils/topological-sort meta-db)]
    (println sorted)
    (clojure.pprint/pprint
     (d/pull-many @meta-db '[:field/name :type/name :param/name] sorted))
    (is (= 1 2))))
