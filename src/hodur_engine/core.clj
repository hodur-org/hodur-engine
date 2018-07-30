(ns hodur-engine.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [datascript.core :as d]))

(def ^:private temp-id-counter (atom 0))

(def ^:private temp-id-map (atom {}))

(def ^:private meta-schema
  {:type/name {:db/unique :db.unique/identity}
   :type/implements  {:db/cardinality :db.cardinality/many
                      :db/valueType   :db.type/ref}
   :type/interface   {:db/index true}

   :field/name       {:db/index true}
   :field/parent     {:db/cardinality :db.cardinality/one
                      :db/valueType   :db.type/ref}
   :field/type       {:db/cardinality :db.cardinality/one
                      :db/valueType   :db.type/ref} 

   :param/name       {:db/index true}
   :param/parent     {:db/cardinality :db.cardinality/one
                      :db/valueType   :db.type/ref}
   :param/type       {:db/cardinality :db.cardinality/one
                      :db/valueType   :db.type/ref}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Some queries
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def all-interfaces
  '[:find [(pull ?t [* {:type/_implements [*]}]) ...]
    :where
    [?t :type/interface true]])

(def all-types
  '[:find [(pull ?t [* {:type/implements [*]
                        :field/_parent [* {:field/type [*]
                                           :param/_parent [* {:param/type [*]}]}]}]) ...]
    :where
    [?t :type/name]])

(def one-type
  '[:find [(pull ?t [* {:type/implements [*]
                        :field/_parent [* {:field/type [*]
                                           :param/_parent [* {:param/type [*]}]}]}]) ...]
    :in $ ?n
    :where
    [?t :type/name ?n]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Internal utils
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^:private schema-files
  [in]
  (->> in
       io/file
       file-seq
       (filter #(string/ends-with?
                 (.getPath ^java.io.File %)
                 ".edn"))))

(defn ^:private conj-vals
  [a coll]
  (reduce (fn [accum i]
            (conj accum i))
          a coll))

(defn ^:private reduce-all-files
  [files]
  (reduce (fn [a file]
            (conj-vals a (-> file slurp edn/read-string)))
          [] files))

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
  ([t i]
   (get-temp-id! (str t "-" i)))
  ([i]
   (if-let [out (get @temp-id-map i)]
     out
     (get (set-temp-id! i) i))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parsing functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^:private implements-reader
  [k coll]
  {:new-v (let [real-coll (if (symbol? coll) [coll] coll)]
            (map (fn [sym] {:db/id (get-temp-id! sym)})
                 real-coll))})

(defn ^:private create-type-reader
  [ns]
  (fn [k sym]
    {:new-k (keyword ns "type")
     :new-v {:db/id (get-temp-id! sym)}}))

(defn ^:private apply-metas
  ([ns t init-map]
   (apply-metas ns t init-map nil))
  ([ns t init-map reader-map]
   (let [meta-data (meta t)]
     (reduce-kv (fn [a k v]
                  (let [ori-k
                        (if (namespace k)
                          k
                          (keyword ns (name k)))
                        {:keys [new-k new-v] :or {new-k ori-k}}
                        (if-let [reader-fn (get reader-map k)]
                          (reader-fn ori-k v)
                          {:new-v v})]
                    (assoc a new-k new-v)))
                init-map
                meta-data))))

(defn ^:private conj-type
  [a t]
  (conj a (apply-metas
           "type" t
           {:db/id (get-temp-id! t)
            :type/name (str t)}
           {:implements implements-reader})))

(defn ^:private conj-params
  [a t field params]
  (reduce (fn [accum param]
            (conj accum (apply-metas
                         "param" param
                         {:param/name (str param)
                          :param/parent {:db/id (get-temp-id! t field)}}
                         {:type (create-type-reader "param")
                          :tag (create-type-reader "param")})))
          a params))

(defn ^:private conj-fields
  [a t fields]
  (loop [accum a
         field (first fields)
         last-field nil
         next-fields (next fields)]
    (if (nil? field)
      accum
      (let [new-accum
            (cond
              (symbol? field)
              (conj accum (apply-metas
                           "field" field
                           {:db/id (get-temp-id! t field)
                            :field/name (str field)
                            :field/parent {:db/id (get-temp-id! t)}}
                           {:type (create-type-reader "field")
                            :tag (create-type-reader "field")}))

              (seqable? field)
              (conj-params accum t last-field field)
              
              :default
              accum)]
        (recur new-accum
               (first next-fields)
               field
               (next next-fields))))))

(defn ^:private parse-types
  [accum types]
  (loop [a accum
         t (first types)
         fields (second types)
         next-t (next (next types))]
    (if-not (nil? t)
      (recur (-> a
                 (conj-type t)
                 (conj-fields t fields))
             (first next-t)
             (second next-t)
             (next (next next-t)))
      a)))

(defn ^:private create-primitive-types
  [accum]
  (reduce (fn [a i]
            (conj a {:db/id (get-temp-id! i)
                     :type/name (str i)}))
          accum '[String Float Integer Boolean DateTime ID]))

(defn ^:private internal-schema
  [source-schema]
  (-> []
      create-primitive-types
      (parse-types source-schema)))

;;TODO
(defn ^:private is-schema-valid?
  [schema] 
  true)

(defn ^:private ensure-meta-db
  [schema]
  (let [conn (d/create-conn meta-schema)]
    (d/transact! conn schema)
    conn))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn init-schema [source-schema]
  (reset-temp-id-state!)
  (let [schema (internal-schema source-schema)]
    (if (is-schema-valid? schema)
      (ensure-meta-db schema))))

(defn init-path [path]
  (-> path
      schema-files
      reduce-all-files
      init-schema))
