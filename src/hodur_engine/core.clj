(ns hodur-engine.core
  (:require [camel-snake-kebab.core :refer [->camelCaseKeyword
                                            ->PascalCaseKeyword
                                            ->kebab-case-keyword
                                            ->snake_case_keyword]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [datascript.core :as d]))

(def ^:private temp-id-counter (atom 0))

(def ^:private temp-id-map (atom {}))

(def ^:private meta-schema
  {;;type meta nodes
   :type/name             {:db/unique :db.unique/identity}
   :type/kebab-case-name  {:db/unique :db.unique/identity}
   :type/PascalCaseName   {:db/unique :db.unique/identity}
   :type/camelCaseName    {:db/unique :db.unique/identity}
   :type/snake_case_name  {:db/unique :db.unique/identity}
   :type/implements       {:db/cardinality :db.cardinality/many
                           :db/valueType   :db.type/ref}
   :type/interface        {:db/index true}
   :type/enum             {:db/index true}
   :type/union            {:db/index true}

   ;;field meta nodes
   :field/name            {:db/index true}
   :field/kebab-case-name {:db/index true}
   :field/PascalCaseName  {:db/index true}
   :field/camelCaseName   {:db/index true}
   :field/snake_case_name {:db/index true}
   :field/parent          {:db/cardinality :db.cardinality/one
                           :db/valueType   :db.type/ref}
   :field/type            {:db/cardinality :db.cardinality/one
                           :db/valueType   :db.type/ref}

   ;;param meta nodes
   :param/name            {:db/index true}
   :param/kebab-case-name {:db/index true}
   :param/PascalCaseName  {:db/index true}
   :param/camelCaseName   {:db/index true}
   :param/snake_case_name {:db/index true}
   :param/parent          {:db/cardinality :db.cardinality/one
                           :db/valueType   :db.type/ref}
   :param/type            {:db/cardinality :db.cardinality/one
                           :db/valueType   :db.type/ref}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; FIXME: move these to a README/TUTORIAL when one is available
;; Some queries
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def all-interfaces
  '[:find [(pull ?t [* {:type/_implements [*]}]) ...]
    :where
    [?t :type/interface true]])

(def all-types
  '[:find [(pull ?t [* {:type/implements [*]
                        :field/_parent
                        [* {:field/type [*]
                            :param/_parent
                            [* {:param/type [*]}]}]}]) ...]
    :where
    [?t :type/name]])

(def one-type
  '[:find [(pull ?t [* {:type/implements [*]
                        :field/_parent
                        [* {:field/type [*]
                            :param/_parent
                            [* {:param/type [*]}]}]}]) ...]
    :in $ ?n
    :where
    [?t :type/name ?n]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Internal utils
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^:private schema-files
  [paths]
  (reduce
   (fn [a path]
     (concat a
             (->> path
                  io/file
                  file-seq
                  (filter #(string/ends-with?
                            (.getPath ^java.io.File %) ".edn")))))
   [] paths))

(defn ^:private slurp-files
  [files] 
  (map #(-> % slurp edn/read-string)
       files))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Temp ID state stuff
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^:private reset-temp-id-state!
  []
  (reset! temp-id-counter 0)
  (reset! temp-id-map {}))

(defn ^:private next-temp-id!
  []
  (swap! temp-id-counter dec))

(defn ^:private set-temp-id!
  [i]
  (swap! temp-id-map assoc i (next-temp-id!)))

(defn ^:private get-temp-id!
  ([t i r]
   (get-temp-id! (str t "-" i "-" r)))
  ([i]
   (if-let [out (get @temp-id-map i)]
     out
     (get (set-temp-id! i) i))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parsing functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^:private implements-reader
  [k & coll]
  {:new-v (map (fn [sym] {:db/id (get-temp-id! sym)})
               (flatten coll))})

(defn ^:private create-type-reader
  [ns]
  (fn [k sym]
    {:new-k (keyword ns "type")
     :new-v {:db/id (get-temp-id! sym)}}))

(defn ^:private expanded-key
  [ns k]
  (if (namespace k)
    k
    (keyword ns (name k))))

(defn ^:private find-and-run-reader
  [reader-map ns k v]
  (let [expanded-k (expanded-key ns k)
        out {:new-k expanded-k
             :new-v v}]
    (if-let [reader-fn (get reader-map k)]
      (merge out (reader-fn expanded-k v))
      out)))

(defn ^:private apply-metas
  ([ns t default init-map]
   (apply-metas ns t init-map nil))
  ([ns t default init-map reader-map]
   (let [meta-data (merge default (meta t))]
     (reduce-kv (fn [a k v]
                  (let [{:keys [new-k new-v]}
                        (find-and-run-reader reader-map ns k v)]
                    (assoc a new-k new-v)))
                init-map
                meta-data))))

(defn ^:private get-recursive
  [e]
  (let [m (meta e)]
    (reduce-kv
     (fn [a k v]
       (if (= "tag-recursive" (name k))
         (assoc a k v)
         a))
     {} m)))

(defn ^:private merge-recursive
  [base rec sym]
  (reduce-kv
   (fn [m k {:keys [only except] :as v}]
     (let [tag-k (keyword (namespace k) "tag")]
       (cond-> m
         (or only except)
         (dissoc tag-k)
         
         (= true v)
         (assoc tag-k true)

         (and only (some #(= sym %) only))
         (assoc tag-k true)

         (and except (not (some #(= sym %) except)))
         (assoc tag-k true))))
   (or base {}) rec))

(defn ^:private conj-type
  [a t default recursive]
  (conj a (apply-metas
           "type" t (merge-recursive default recursive t)
           {:db/id (get-temp-id! t)
            :type/name (str t)
            :type/kebab-case-name (->kebab-case-keyword t)
            :type/camelCaseName (->camelCaseKeyword t)
            :type/PascalCaseName (->PascalCaseKeyword t)
            :type/snake_case_name (->snake_case_keyword t)
            :type/nature :user}
           {:implements implements-reader})))

(defn ^:private conj-params
  [a t field r params default recursive]
  (reduce (fn [accum param]
            (conj accum (apply-metas
                         "param" param (merge-recursive default recursive param)
                         {:param/name (str param)
                          :param/kebab-case-name (->kebab-case-keyword param)
                          :param/PascalCaseName (->PascalCaseKeyword param)
                          :param/camelCaseName (->camelCaseKeyword param)
                          :param/snake_case_name (->snake_case_keyword param)
                          :param/parent {:db/id (get-temp-id! t field r)}}
                         {:type (create-type-reader "param")
                          :tag (create-type-reader "param")})))
          a params))

(defn ^:private conj-fields
  [a t fields default recursive]
  (loop [accum a
         field (first fields)
         last-field nil
         last-r nil
         next-fields (next fields)]
    (if (nil? field)
      accum
      (let [r (rand)
            new-accum
            (cond
              ;; is a field proper
              (symbol? field)
              (let [recursive (merge recursive (get-recursive field))
                    merged-default (merge-recursive default recursive field)
                    init-map {:db/id (get-temp-id! t field r)
                              :field/name (str field)
                              :field/kebab-case-name (->kebab-case-keyword field)
                              :field/PascalCaseName (->PascalCaseKeyword field)
                              :field/camelCaseName (->camelCaseKeyword field)
                              :field/snake_case_name (->snake_case_keyword field)
                              :field/parent {:db/id (get-temp-id! t)}}]
                (conj accum (apply-metas
                             "field" field
                             merged-default
                             init-map
                             {:type (create-type-reader "field")
                              :tag (create-type-reader "field")})))
              
              ;; is a coll of params
              (seqable? field)
              (let [recursive (merge recursive (get-recursive last-field))]
                (conj-params accum t last-field last-r field
                             default recursive))
              
              :default
              accum)]
        (recur new-accum
               (first next-fields)
               field
               r
               (next next-fields))))))

(defn ^:private parse-types
  [accum types]
  (let [has-default? (= (first types) 'default)
        real-types (if has-default? (next types) types)
        default (if has-default? (meta (first types)) {})]
    (loop [a accum
           t (first real-types)
           fields (second real-types)
           next-t (next (next real-types))]
      (if-not (nil? t)
        (let [recursive (get-recursive t)]
          (recur (-> a
                     (conj-type t default recursive)
                     (conj-fields t fields default recursive))
                 (first next-t)
                 (second next-t)
                 (next (next next-t))))
        a))))

(defn ^:private parse-type-groups
  [accum type-groups]
  (reduce (fn [a type-group]
            (parse-types a type-group))
          accum
          type-groups))

(defn ^:private create-primitive-types
  [accum]
  (reduce (fn [a i]
            (conj a {:db/id (get-temp-id! i)
                     :type/name (str i)
                     :type/nature :primitive}))
          accum '[String Float Integer Boolean DateTime ID]))

(defn ^:private internal-schema
  [source-schemas]
  (-> []
      create-primitive-types
      (parse-type-groups source-schemas)))

;;TODO
(defn ^:private is-schema-valid?
  [schema] 
  true)

(defn ^:private ensure-meta-db
  [schema]
  #_(clojure.pprint/pprint schema)
  (let [conn (d/create-conn meta-schema)]
    (d/transact! conn schema)
    conn))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn init-schema [source-schema & others]
  (reset-temp-id-state!)
  (let [source-schemas (conj others source-schema)
        schema (internal-schema source-schemas)] 
    (if (is-schema-valid? schema)
      (ensure-meta-db schema))))

(defn init-path [path & others]
  (let [paths (-> others flatten (conj path) flatten)]
    (->> paths
         schema-files
         slurp-files
         (apply init-schema))))

#_(let [datomic-c (init-path "test/schemas/several/datomic"
                             "test/schemas/several/shared")]
    (clojure.pprint/pprint
     (map #(cond-> {}
             (:type/name %) (assoc :type (:type/name %))
             (:field/name %) (assoc :field (:field/name %))
             (:param/name %) (assoc :param (:param/name %)))
          (d/q '[:find [(pull ?e [*]) ...]
                 :where
                 [?e :datomic/tag true]]
               @datomic-c))))

#_(let [#_lacinia-c #_(init-path "test/schemas/several/lacinia"
                                 "test/schemas/several/shared")
        datomic-c (init-path "test/schemas/several/datomic"
                             "test/schemas/several/shared")]
    #_(clojure.pprint/pprint
       (d/q '[:find [(pull ?e [*]) ...]
              :where
              [?e :datomic/tag true]]
            @datomic-c)))
