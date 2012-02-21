(ns class-finder.core
  (:import com.offbytwo.class_finder.ClassFinder))

(def ^:private class-finder (atom (ClassFinder.)))

(defn- glob? [name] (or (.contains name "*") (.contains name "?")))

(defn find-classes
  ([classname]
     (let [classes (if (glob? classname)
                     (.findClassesByGlob @class-finder classname)
                     (.findClassesByName @class-finder classname))]
       (apply list classes)))
  ([classname package-prefix]
     (let [classes (if (glob? classname)
                     (.findClassesInPackageByGlob @class-finder
                                                  package-prefix classname)
                     (.findClassesInPackageByName @class-finder
                                                  package-prefix classname))]
       (apply list classes))))
