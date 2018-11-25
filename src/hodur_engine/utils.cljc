(ns hodur-engine.utils
  (:require [clojure.set :refer [difference union intersection]]
            [clojure.string :as string]
            [datascript.core :as d]
            [datascript.query-v3 :as q]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Topological Sorting
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^:private without
  "Returns set s with x removed."
  [s x] (difference s #{x}))

(defn ^:private take-1
  "Returns the pair [element, s'] where s' is set s with element removed."
  [s] {:pre [(not (empty? s))]}
  (let [item (first s)]
    [item (without s item)]))

(defn ^:private no-incoming
  "Returns the set of nodes in graph g for which there are no incoming
  edges, where g is a map of nodes to sets of nodes."
  [g]
  (let [nodes (set (keys g))
        have-incoming (apply union (vals g))]
    (difference nodes have-incoming)))

(defn ^:private normalize
  "Returns g with empty outgoing edges added for nodes with incoming
  edges only.  Example: {:a #{:b}} => {:a #{:b}, :b #{}}"
  [g]
  (let [have-incoming (apply union (vals g))]
    (reduce #(if (get % %2) % (assoc % %2 #{})) g have-incoming)))

(defn ^:private kahn-sort
  "Proposes a topological sort for directed graph g using Kahn's
   algorithm, where g is a map of nodes to sets of nodes. If g is
   cyclic, returns nil."
  ([g]
   (kahn-sort (normalize g) [] (no-incoming g)))
  ([g l s]
   (if (empty? s)
     (when (every? empty? (vals g)) l)
     (let [[n s'] (take-1 s)
           m (g n)
           g' (reduce #(update-in % [n] without %2) g m)]
       (recur g' (conj l n) (union s' (intersection (no-incoming g') m)))))))

(defn ^:private all-ids
  "Returns all ids of all entity nodes. When an optional map is passed
  with a tag, the ids are filtered by that tag."
  ([conn]
   (all-ids conn nil))
  ([conn {:keys [tag]}]
   (if tag
     (d/q '[:find [?e ...]
            :in $ ?tag
            :where
            [?e ?tag true]]
          @conn tag)
     (d/q '[:find [?e ...]
            :where
            [?e]]
          @conn))))

(defn ^:private dependency-direction->where
  "This function creates a datalog where clause out of a depdency
  direction map. Such a map has the following 6 mandatory entries each
  indicating the direction of the dependency flow:

  - `:type->field-children` - the relationship between types and their field children
  - `:field->param-children` - the relationship between fields and their param children
  - `:type->field-return` - the relationship between types and the fields returning them
  - `type->param-return` - the relationship between types and the params returning them
  - `interface->type` - the relationship between interfaces and the types implementing them
  - `union->type` - the relationship between union types and the types that the unify

  The value must be either `:ltr` or `:rtl` which indicate the
  direction of the dependency arrow (i.e. a `:rtl` on
  `:type->field-children` means that the type's fields must be
  declared before the type itself while a `:ltr` signifies that the
  type must be defined prior to its field children."
  [{:keys [type->field-children
           field->param-children
           type->field-return
           type->param-return
           interface->type
           union->type]}]
  (let [entries (cond-> []
                  (= :ltr type->field-children)  (conj '[?e :field/parent ?id])
                  (= :ltr field->param-children) (conj '[?e :param/parent ?id])
                  (= :ltr type->field-return)    (conj '[?e :field/type ?id])
                  (= :ltr type->param-return)    (conj '[?e :param/type ?id])
                  (= :ltr interface->type)       (conj '[?e :type/implements ?id])
                  (= :ltr union->type)           (conj '[?e :field/union-type ?id])
                  
                  (= :rtl type->field-children)  (conj '[?id :field/parent ?e])
                  (= :rtl field->param-children) (conj '[?id :param/parent ?e])
                  (= :rtl type->field-return)    (conj '[?id :field/type ?e])
                  (= :rtl type->param-return)    (conj '[?id :param/type ?e])
                  (= :rtl interface->type)       (conj '[?id :type/implements ?e])
                  (= :rtl union->type)           (conj '[?id :field/union-type ?e]))]
    (list* 'or entries)))

(defn ^:private dependencies-by-id
  "Returns all the ids of all the nodes that depend on the provided
  direction map and node id."
  [conn direction id]
  (let [where (dependency-direction->where direction)
        eids (q/q `[:find [~'?e ...]
                    :in ~'$ ~'?id
                    :where ~where]
                  @conn id)]
    (-> eids vec flatten)))

(defn ^:private dependency-map
  "Creates a map of dependencies compatible with `kahn-sort` for
  topological sorting. The second parameter is a map containing a
  `:direction` and a `:tag`. The former contains the direction map to
  be used and the latter is an optional tag to filter the nodes by."
  [conn {:keys [direction] :or {direction :down} :as opts}]
  (reduce (fn [m id]
            (assoc m id (set (dependencies-by-id conn direction id))))
          {} (all-ids conn opts)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn topological-sort
  "Returns either a topologically sorted vector with the ids of the
  nodes if the graph is acyclical or an empty vector if the graph is
  cyclical.

  The first parameter `conn` is a meta-db atom and the second,
  optional is a map with `:direction` and `:tag` where the former
  contains the direction map to be used and the latter is an optional
  tag to filter the nodes by.

  The direction map must contain the following entries:
  
  - `:type->field-children` - the relationship between types and their field children
  - `:field->param-children` - the relationship between fields and their param children
  - `:type->field-return` - the relationship between types and the fields returning them
  - `type->param-return` - the relationship between types and the params returning them
  - `interface->type` - the relationship between interfaces and the types implementing them
  - `union->type` - the relationship between union types and the types that the unify

  The value must be either `:ltr` or `:rtl` which indicate the
  direction of the dependency arrow (i.e. a `:rtl` on
  `:type->field-children` means that the type's fields must be
  declared before the type itself while a `:ltr` signifies that the
  type must be defined prior to its field children.

  If `opts` is not provided or a `:direction` entry is missing, a
  default direction with all dependencies set to `:ltr` will be used."
  ([conn]
   (topological-sort conn nil))
  ([conn {:keys [direction] :as opts}]
   (let [opts' (if (not direction)
                 (assoc opts
                        {:direction {:type->field-children :ltr
                                     :field->param-children :ltr
                                     :type->field-return :ltr
                                     :type->param-return :ltr
                                     :interface->type :ltr
                                     :union->type :ltr}})
                 opts)])
   (-> conn
       (dependency-map opts')
       kahn-sort)))

#_(let [meta-db (init-schema
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
        sorted (topological-sort meta-db {:direction {:type->field-children :rtl
                                                      :field->param-children :rtl
                                                      :type->field-return :rtl
                                                      :type->param-return :rtl
                                                      :interface->implements :rtl
                                                      :union->type :rtl}})]
    (println sorted)
    (clojure.pprint/pprint
     (d/pull-many @meta-db '[:field/name :type/name :param/name] sorted)))
